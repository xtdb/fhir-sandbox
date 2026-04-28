import postgres from 'postgres';
import { randomUUID } from 'node:crypto';

const host          = process.env.XTDB_HOST     || 'localhost';
const port          = parseInt(process.env.XTDB_PORT || '5432', 10);
const db            = process.env.XTDB_DB       || 'xtdb';
const user          = process.env.XTDB_USER     || 'xtdb';
const mode          = process.env.MODE          || 'batched';
const totalRows     = parseInt(process.env.TOTAL_ROWS   || '500000', 10);
const rowsPerTxn    = parseInt(process.env.ROWS_PER_TXN || '1000', 10);
const warmupTxns    = parseInt(process.env.WARMUP_TXNS  || '5', 10);

if (mode !== 'batched' && mode !== 'sequential') {
  throw new Error(`MODE must be 'batched' or 'sequential', got: ${mode}`);
}
const numTxns = Math.floor(totalRows / rowsPerTxn);
if (numTxns <= warmupTxns) {
  throw new Error('TOTAL_ROWS/ROWS_PER_TXN must exceed WARMUP_TXNS');
}

// XTDB refuses DML with OID-0 (unknown) parameters. Postgres.js sends strings
// with OID 0 by default, so we register explicit UUID and TEXT types and use
// sql.typed.uuid(...) / sql.typed.text(...) at the call sites to force the OIDs
// into the Parse message.
const sql = postgres({
  host,
  port,
  database: db,
  username: user,
  max: 1,
  prepare: true,
  fetch_types: true,
  types: {
    uuid: {
      to: 2950,
      from: [2950],
      serialize: (x) => x,
      parse: (x) => x,
    },
    text: {
      to: 25,
      from: [25, 1043],
      serialize: (x) => x,
      parse: (x) => x,
    },
  },
});

console.log('=== bench-postgresjs ===');
console.log(`host=${host} port=${port} db=${db}`);
console.log(`mode=${mode} total_rows=${totalRows} rows_per_txn=${rowsPerTxn} warmup_txns=${warmupTxns}`);

const measuredCount = numTxns - warmupTxns;
const totalsNs = new Array(measuredCount);
const buildsNs = new Array(measuredCount);
const waitsNs  = new Array(measuredCount);
let resultIdx = 0;
let wallMeasuredStart = 0n;

for (let t = 0; t < numTxns; t++) {
  if (t === warmupTxns) {
    wallMeasuredStart = process.hrtime.bigint();
  }

  let phaseBuild = 0n;
  let phaseWait  = 0n;
  const txStart = process.hrtime.bigint();

  await sql.begin(async tx => {
    if (mode === 'batched') {
      const b0 = process.hrtime.bigint();
      const queries = new Array(rowsPerTxn);
      for (let i = 0; i < rowsPerTxn; i++) {
        queries[i] = tx`INSERT INTO bench (_id, payload, _valid_to) VALUES (${sql.typed.uuid(randomUUID())}, ${sql.typed.text(`payload-${t}-${i}`)}, CURRENT_TIMESTAMP + INTERVAL 'PT1M')`.execute();
      }
      const b1 = process.hrtime.bigint();
      await Promise.all(queries);
      const w1 = process.hrtime.bigint();
      phaseBuild = b1 - b0;
      phaseWait  = w1 - b1;
    } else {
      for (let i = 0; i < rowsPerTxn; i++) {
        const b0 = process.hrtime.bigint();
        const q = tx`INSERT INTO bench (_id, payload, _valid_to) VALUES (${sql.typed.uuid(randomUUID())}, ${sql.typed.text(`payload-${t}-${i}`)}, CURRENT_TIMESTAMP + INTERVAL 'PT1M')`;
        const b1 = process.hrtime.bigint();
        await q;
        const w1 = process.hrtime.bigint();
        phaseBuild += (b1 - b0);
        phaseWait  += (w1 - b1);
      }
    }
  });

  const took = process.hrtime.bigint() - txStart;
  if (t >= warmupTxns) {
    totalsNs[resultIdx] = took;
    buildsNs[resultIdx] = phaseBuild;
    waitsNs[resultIdx]  = phaseWait;
    resultIdx++;
  }
}

const wallMeasuredEnd = process.hrtime.bigint();
const measuredRows = measuredCount * rowsPerTxn;
const secs = Number(wallMeasuredEnd - wallMeasuredStart) / 1e9;

// "other" captures the sql.begin() wrapper cost: implicit BEGIN + COMMIT
// round trips plus library bookkeeping (the driver sends BEGIN before our
// body runs and COMMIT after it returns).
const othersNs = totalsNs.map((t, i) => t - buildsNs[i] - waitsNs[i]);

printStats(mode, measuredRows, secs, rowsPerTxn, totalsNs, buildsNs, waitsNs, othersNs);

await sql.end();

function printPhase(name, ns) {
  const sorted = [...ns].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
  const toMs = (x) => Number(x) / 1e6;
  const p50 = toMs(sorted[Math.floor(sorted.length * 0.50)]);
  const p99 = toMs(sorted[Math.min(Math.floor(sorted.length * 0.99), sorted.length - 1)]);
  let sum = 0n;
  for (const d of sorted) sum += d;
  const mean = toMs(sum / BigInt(sorted.length));
  console.log(`${name.padEnd(10)} ${mean.toFixed(2).padStart(10)} ${p50.toFixed(2).padStart(10)} ${p99.toFixed(2).padStart(10)}`);
}

function printStats(mode, rows, secs, rowsPerTxn, totalsNs, buildsNs, waitsNs, othersNs) {
  const rps = rows / secs;
  console.log('');
  console.log('=== results ===');
  console.log(`mode             : ${mode}`);
  console.log(`measured rows    : ${rows}`);
  console.log(`measured seconds : ${secs.toFixed(3)}`);
  console.log(`rows/sec         : ${rps.toFixed(1)}`);
  console.log(`txn/sec          : ${(rps / rowsPerTxn).toFixed(2)}`);
  console.log('');
  console.log('txn phase latencies (per transaction):');
  console.log(`${'phase'.padEnd(10)} ${'mean(ms)'.padStart(10)} ${'p50(ms)'.padStart(10)} ${'p99(ms)'.padStart(10)}`);
  printPhase('total', totalsNs);
  printPhase('build', buildsNs);
  printPhase('wait',  waitsNs);
  printPhase('other', othersNs);
  console.log('');
  console.log('  build = client CPU (UUID, template, .execute() enqueue)');
  console.log('  wait  = client blocked on server (Promise.all / per-query await)');
  console.log('  other = sql.begin() wrapper: BEGIN + COMMIT round trips + library bookkeeping');
}