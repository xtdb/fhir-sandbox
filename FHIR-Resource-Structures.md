# FHIR Resource Type Structures

This document describes all 22 FHIR resource types found in the `xtdb-fhir/data/fhir` folder. Each resource type could be represented as a database table.

---

## Table of Contents

1. [AllergyIntolerance](#1-allergyintolerance)
2. [CarePlan](#2-careplan)
3. [CareTeam](#3-careteam)
4. [Claim](#4-claim)
5. [Condition](#5-condition)
6. [Coverage](#6-coverage)
7. [Device](#7-device)
8. [DiagnosticReport](#8-diagnosticreport)
9. [DocumentReference](#9-documentreference)
10. [Encounter](#10-encounter)
11. [ExplanationOfBenefit](#11-explanationofbenefit)
12. [ImagingStudy](#12-imagingstudy)
13. [Immunization](#13-immunization)
14. [Medication](#14-medication)
15. [MedicationAdministration](#15-medicationadministration)
16. [MedicationRequest](#16-medicationrequest)
17. [Observation](#17-observation)
18. [Patient](#18-patient)
19. [Procedure](#19-procedure)
20. [Provenance](#20-provenance)
21. [ServiceRequest](#21-servicerequest)
22. [SupplyDelivery](#22-supplydelivery)

---

## 1. AllergyIntolerance

Records of patient allergies and intolerances to substances.

```
AllergyIntolerance
├── id: string                        # Unique identifier for this allergy record
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this resource conforms to (e.g., US Core)
├── type: string                      # "allergy" or "intolerance" - nature of the reaction
├── criticality: string               # "low", "high", or "unable-to-assess" - potential severity
├── recordedDate: string              # Date this allergy was first recorded (ISO 8601)
├── category[]: string                # Category: "food", "medication", "environment", "biologic"
├── clinicalStatus                    # Whether allergy is active, inactive, or resolved
│   └── coding[]                      # Standardized codes for the status
│       ├── code: string              # Status code (e.g., "active", "resolved")
│       └── system: string            # Code system URL (e.g., terminology.hl7.org)
├── verificationStatus                # Confidence level: confirmed, unconfirmed, refuted
│   └── coding[]                      # Standardized codes for verification
│       ├── code: string              # Verification code (e.g., "confirmed")
│       └── system: string            # Code system URL
├── code                              # The substance that causes the allergy
│   ├── text: string                  # Human-readable name of allergen (e.g., "Penicillin")
│   └── coding[]                      # Standardized allergen codes
│       ├── code: string              # Code for the allergen (e.g., RxNorm, SNOMED CT)
│       ├── display: string           # Display name for the code
│       └── system: string            # Code system (e.g., SNOMED CT URL)
├── patient                           # Reference to the patient with this allergy
│   └── reference: string             # Patient resource reference (e.g., "Patient/123")
└── reaction[]                        # List of adverse reactions when exposed
    ├── severity: string              # "mild", "moderate", or "severe"
    └── manifestation[]               # How the reaction presents (symptoms)
        ├── text: string              # Human-readable symptom description
        └── coding[]                  # Standardized symptom codes
            ├── code: string          # SNOMED code for manifestation
            ├── display: string       # Display name (e.g., "Hives", "Anaphylaxis")
            └── system: string        # Code system URL
```

---

## 2. CarePlan

Healthcare plans designed for patient care management.

```
CarePlan
├── id: string                        # Unique identifier for this care plan
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "draft", "active", "completed", "cancelled", etc.
├── intent: string                    # "proposal", "plan", "order", "option"
├── text                              # Human-readable summary of the care plan
│   ├── div: string                   # XHTML content for display
│   └── status: string                # "generated", "extensions", "additional"
├── period                            # Time period the care plan covers
│   ├── start: string                 # Start date/time (ISO 8601)
│   └── end: string                   # End date/time (ISO 8601)
├── subject                           # The patient this care plan is for
│   └── reference: string             # Patient resource reference
├── encounter                         # The encounter that initiated this care plan
│   └── reference: string             # Encounter resource reference
├── careTeam[]                        # Who is involved in this care plan
│   └── reference: string             # CareTeam resource reference
├── addresses[]                       # Health issues this plan addresses
│   └── reference: string             # Condition resource reference
├── category[]                        # Type of care plan (e.g., disease management)
│   └── coding[]                      # Standardized category codes
│       ├── code: string              # Category code
│       └── system: string            # Code system URL
└── activity[]                        # Planned actions in this care plan
    └── detail                        # Details of the planned activity
        ├── status: string            # "not-started", "in-progress", "completed"
        ├── location                  # Where the activity should occur
        │   └── display: string       # Location name
        ├── code                      # What activity is planned
        │   ├── text: string          # Human-readable activity description
        │   └── coding[]              # Standardized activity codes
        │       ├── code: string      # Activity code (e.g., SNOMED procedure)
        │       ├── display: string   # Display name for the activity
        │       └── system: string    # Code system URL
        └── reasonReference[]         # Why this activity is needed
            └── reference: string     # Condition reference explaining the reason
```

---

## 3. CareTeam

Groups of practitioners and organizations involved in patient care.

```
CareTeam
├── id: string                        # Unique identifier for this care team
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "proposed", "active", "suspended", "inactive"
├── period                            # Time period the team is/was active
│   ├── start: string                 # When the team was formed (ISO 8601)
│   └── end: string                   # When the team was disbanded (ISO 8601)
├── subject                           # The patient this team cares for
│   └── reference: string             # Patient resource reference
├── encounter                         # Encounter that created this team
│   └── reference: string             # Encounter resource reference
├── managingOrganization[]            # Organization responsible for the team
│   ├── display: string               # Organization name for display
│   └── reference: string             # Organization resource reference
├── reasonCode[]                      # Why the care team exists
│   ├── text: string                  # Human-readable reason
│   └── coding[]                      # Standardized reason codes
│       ├── code: string              # Condition/reason code
│       ├── display: string           # Display name for the reason
│       └── system: string            # Code system URL
└── participant[]                     # Members of the care team
    ├── member                        # The actual team member
    │   ├── display: string           # Member's name for display
    │   └── reference: string         # Practitioner/Organization reference
    └── role[]                        # What role this member plays
        ├── text: string              # Human-readable role description
        └── coding[]                  # Standardized role codes
            ├── code: string          # Role code (e.g., "primary care physician")
            ├── display: string       # Display name for the role
            └── system: string        # Code system URL
```

---

## 4. Claim

Insurance claims submitted for healthcare services.

```
Claim
├── id: string                        # Unique identifier for this claim
├── status: string                    # "active", "cancelled", "draft", "entered-in-error"
├── use: string                       # "claim", "preauthorization", "predetermination"
├── created: string                   # When the claim was created (ISO 8601)
├── billablePeriod                    # Period of service being billed
│   ├── start: string                 # Service start date
│   └── end: string                   # Service end date
├── total                             # Total claim amount
│   ├── value: float                  # Dollar amount (e.g., 150.00)
│   └── currency: string              # Currency code (e.g., "USD")
├── type                              # Category of claim (institutional, professional, etc.)
│   └── coding[]                      # Standardized claim type codes
│       ├── code: string              # Claim type code (e.g., "professional")
│       └── system: string            # Code system URL
├── priority                          # Processing priority
│   └── coding[]                      # Priority codes
│       ├── code: string              # Priority level (e.g., "normal", "stat")
│       └── system: string            # Code system URL
├── patient                           # The patient who received services
│   ├── display: string               # Patient name for display
│   └── reference: string             # Patient resource reference
├── provider                          # The provider submitting the claim
│   ├── display: string               # Provider name for display
│   └── reference: string             # Practitioner/Organization reference
├── facility                          # Where services were rendered
│   ├── display: string               # Facility name
│   └── reference: string             # Location resource reference
├── prescription                      # Related prescription if applicable
│   └── reference: string             # MedicationRequest reference
├── insurance[]                       # Insurance coverage for this claim
│   ├── focal: boolean                # Is this the primary insurance?
│   ├── sequence: integer             # Order of insurance (1=primary, 2=secondary)
│   └── coverage                      # The actual coverage
│       └── display: string           # Insurance plan name
├── diagnosis[]                       # Diagnoses relevant to this claim
│   ├── sequence: integer             # Order of diagnosis (1=primary)
│   └── diagnosisReference            # Reference to the diagnosis
│       └── reference: string         # Condition resource reference
├── procedure[]                       # Procedures performed
│   ├── sequence: integer             # Order of procedure
│   └── procedureReference            # Reference to the procedure
│       └── reference: string         # Procedure resource reference
├── supportingInfo[]                  # Additional claim information
│   ├── sequence: integer             # Order of supporting info
│   ├── category                      # Type of supporting info
│   │   └── coding[]                  # Category codes
│   │       ├── code: string          # Category code
│   │       └── system: string        # Code system URL
│   └── valueReference                # The supporting information
│       └── reference: string         # Reference to supporting resource
└── item[]                            # Line items being billed
    ├── sequence: integer             # Line item number
    ├── encounter[]                   # Related encounter
    │   └── reference: string         # Encounter resource reference
    └── productOrService              # What service/product is being billed
        ├── text: string              # Human-readable description
        └── coding[]                  # Standardized service codes
            ├── code: string          # CPT/HCPCS code for the service
            ├── display: string       # Service description
            └── system: string        # Code system (e.g., CPT)
```

---

## 5. Condition

Clinical conditions, diagnoses, or health problems.

```
Condition
├── id: string                        # Unique identifier for this condition
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── onsetDateTime: string             # When the condition started (ISO 8601)
├── abatementDateTime: string         # When the condition resolved (ISO 8601)
├── recordedDate: string              # When this was first recorded (ISO 8601)
├── clinicalStatus                    # Current status of the condition
│   └── coding[]                      # Status codes
│       ├── code: string              # "active", "recurrence", "relapse", "inactive", "remission", "resolved"
│       └── system: string            # Code system URL
├── verificationStatus                # Certainty of the diagnosis
│   └── coding[]                      # Verification codes
│       ├── code: string              # "unconfirmed", "provisional", "differential", "confirmed", "refuted"
│       └── system: string            # Code system URL
├── category[]                        # Type of condition
│   └── coding[]                      # Category codes
│       ├── code: string              # Category (e.g., "encounter-diagnosis", "problem-list-item")
│       ├── display: string           # Display name
│       └── system: string            # Code system URL
├── code                              # The actual diagnosis/condition
│   ├── text: string                  # Human-readable condition name (e.g., "Type 2 Diabetes")
│   └── coding[]                      # Standardized condition codes
│       ├── code: string              # ICD-10 or SNOMED CT code
│       ├── display: string           # Official name for the code
│       └── system: string            # Code system (ICD-10, SNOMED CT)
├── subject                           # The patient with this condition
│   └── reference: string             # Patient resource reference
└── encounter                         # Encounter when condition was diagnosed
    └── reference: string             # Encounter resource reference
```

---

## 6. Coverage

Insurance coverage information for patients.

```
Coverage
├── id: string                        # Unique identifier for this coverage record
├── status: string                    # "active", "cancelled", "draft", "entered-in-error"
├── type                              # Type of coverage
│   └── text: string                  # Coverage type description (e.g., "NO_INSURANCE", "Medicare")
├── beneficiary                       # The patient covered by this insurance
│   └── reference: string             # Patient resource reference
└── payor[]                           # Who pays for the coverage
    └── display: string               # Insurance company name (e.g., "Blue Cross Blue Shield")
```

---

## 7. Device

Medical devices used in patient care.

```
Device
├── id: string                        # Unique identifier for this device record
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "active", "inactive", "entered-in-error", "unknown"
├── distinctIdentifier: string        # Distinct identification string (model-specific)
├── manufactureDate: string           # When the device was manufactured (ISO 8601)
├── expirationDate: string            # When the device expires (ISO 8601)
├── lotNumber: string                 # Lot or batch number for tracking recalls
├── serialNumber: string              # Unique serial number of this specific device
├── type                              # What kind of device this is
│   ├── text: string                  # Human-readable device type (e.g., "Cardiac Pacemaker")
│   └── coding[]                      # Standardized device codes
│       ├── code: string              # Device code (e.g., SNOMED, GMDN)
│       ├── display: string           # Device type name
│       └── system: string            # Code system URL
├── patient                           # Patient this device is associated with
│   └── reference: string             # Patient resource reference
├── deviceName[]                      # Names for this device
│   ├── name: string                  # Device name (e.g., "Medtronic Pacemaker Model X")
│   └── type: string                  # Name type: "udi-label-name", "user-friendly-name", etc.
└── udiCarrier[]                      # Unique Device Identifier (UDI) barcode info
    ├── deviceIdentifier: string      # Primary identifier from UDI barcode
    └── carrierHRF: string            # Human-readable form of the full UDI
```

---

## 8. DiagnosticReport

Results of diagnostic tests and imaging studies.

```
DiagnosticReport
├── id: string                        # Unique identifier for this report
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "registered", "partial", "preliminary", "final", "amended"
├── effectiveDateTime: string         # When the diagnostic was performed (ISO 8601)
├── issued: string                    # When the report was released (ISO 8601)
├── category[]                        # Service category (lab, radiology, pathology, etc.)
│   └── coding[]                      # Category codes
│       ├── code: string              # Category code (e.g., "LAB", "RAD")
│       ├── display: string           # Display name (e.g., "Laboratory", "Radiology")
│       └── system: string            # Code system URL
├── code                              # What test/panel was ordered
│   ├── text: string                  # Human-readable test name (e.g., "Complete Blood Count")
│   └── coding[]                      # Standardized test codes
│       ├── code: string              # LOINC code for the test
│       ├── display: string           # Test name
│       └── system: string            # Code system (usually LOINC)
├── subject                           # Patient this report is about
│   └── reference: string             # Patient resource reference
├── encounter                         # Healthcare encounter for this test
│   └── reference: string             # Encounter resource reference
├── performer[]                       # Who performed/interpreted the test
│   ├── display: string               # Performer name (lab, radiologist)
│   └── reference: string             # Practitioner/Organization reference
├── result[]                          # Individual test results (Observations)
│   ├── display: string               # Result summary for display
│   └── reference: string             # Observation resource reference
└── presentedForm[]                   # Full report as attachment (PDF, etc.)
    ├── contentType: string           # MIME type (e.g., "application/pdf")
    └── data: string (base64)         # Base64-encoded report content
```

---

## 9. DocumentReference

References to clinical documents.

```
DocumentReference
├── id: string                        # Unique identifier for this document reference
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "current", "superseded", "entered-in-error"
├── date: string                      # When document was created (ISO 8601)
├── identifier[]                      # External identifiers for this document
│   ├── system: string                # Identifier system (e.g., organization OID)
│   └── value: string                 # Identifier value
├── type                              # Kind of document (discharge summary, progress note, etc.)
│   └── coding[]                      # Document type codes
│       ├── code: string              # LOINC document type code
│       ├── display: string           # Document type name
│       └── system: string            # Code system (usually LOINC)
├── category[]                        # High-level document category
│   └── coding[]                      # Category codes
│       ├── code: string              # Category code
│       ├── display: string           # Category name (e.g., "Clinical Note")
│       └── system: string            # Code system URL
├── subject                           # Patient this document is about
│   └── reference: string             # Patient resource reference
├── author[]                          # Who created the document
│   ├── display: string               # Author name
│   └── reference: string             # Practitioner reference
├── custodian                         # Organization maintaining the document
│   ├── display: string               # Organization name
│   └── reference: string             # Organization resource reference
├── content[]                         # The actual document content
│   ├── attachment                    # Document data
│   │   ├── contentType: string       # MIME type (e.g., "text/html", "application/pdf")
│   │   └── data: string (base64)     # Base64-encoded document content
│   └── format                        # Format of the content
│       ├── code: string              # Format code (e.g., CDA, FHIR)
│       ├── display: string           # Format name
│       └── system: string            # Code system URL
└── context                           # Clinical context of the document
    ├── encounter[]                   # Related encounter
    │   └── reference: string         # Encounter resource reference
    └── period                        # Time period document covers
        ├── start: string             # Period start (ISO 8601)
        └── end: string               # Period end (ISO 8601)
```

---

## 10. Encounter

Patient visits and healthcare interactions.

```
Encounter
├── id: string                        # Unique identifier for this encounter
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "planned", "arrived", "in-progress", "finished", "cancelled"
├── class                             # Classification of encounter
│   ├── code: string                  # "AMB" (ambulatory), "IMP" (inpatient), "EMER" (emergency)
│   └── system: string                # Code system for encounter class
├── period                            # When the encounter occurred
│   ├── start: string                 # Check-in time (ISO 8601)
│   └── end: string                   # Discharge/checkout time (ISO 8601)
├── identifier[]                      # External identifiers (visit number, etc.)
│   ├── use: string                   # "usual", "official", "temp", "secondary"
│   ├── system: string                # Identifier system
│   └── value: string                 # Visit/encounter number
├── type[]                            # Specific type of encounter
│   ├── text: string                  # Human-readable encounter type
│   └── coding[]                      # Encounter type codes
│       ├── code: string              # CPT/SNOMED code for encounter type
│       ├── display: string           # Encounter type name (e.g., "General Examination")
│       └── system: string            # Code system URL
├── subject                           # The patient in this encounter
│   ├── display: string               # Patient name for display
│   └── reference: string             # Patient resource reference
├── serviceProvider                   # Organization providing care
│   ├── display: string               # Organization/facility name
│   └── reference: string             # Organization resource reference
├── reasonCode[]                      # Why the encounter took place
│   └── coding[]                      # Reason codes
│       ├── code: string              # Diagnosis/reason code (ICD-10, SNOMED)
│       ├── display: string           # Reason description
│       └── system: string            # Code system URL
├── participant[]                     # Healthcare providers involved
│   ├── individual                    # The actual provider
│   │   ├── display: string           # Provider name
│   │   └── reference: string         # Practitioner resource reference
│   ├── period                        # When they participated
│   │   ├── start: string             # Participation start time
│   │   └── end: string               # Participation end time
│   └── type[]                        # Role in the encounter
│       ├── text: string              # Role description
│       └── coding[]                  # Role codes
│           ├── code: string          # Participation type code
│           ├── display: string       # Role name (e.g., "Attending", "Consultant")
│           └── system: string        # Code system URL
├── location[]                        # Where the encounter happened
│   └── location                      # Location details
│       ├── display: string           # Location name (e.g., "Room 302")
│       └── reference: string         # Location resource reference
└── hospitalization                   # Details if this was an inpatient stay
    └── dischargeDisposition          # Where patient went after discharge
        ├── text: string              # Human-readable disposition
        └── coding[]                  # Disposition codes
            ├── code: string          # Discharge disposition code
            ├── display: string       # Disposition (e.g., "Home", "SNF", "Expired")
            └── system: string        # Code system URL
```

---

## 11. ExplanationOfBenefit

Detailed explanation of insurance benefits and payments.

```
ExplanationOfBenefit
├── id: string                        # Unique identifier for this EOB
├── status: string                    # "active", "cancelled", "draft", "entered-in-error"
├── use: string                       # "claim", "preauthorization", "predetermination"
├── outcome: string                   # "queued", "complete", "error", "partial"
├── created: string                   # When EOB was created (ISO 8601)
├── billablePeriod                    # Service period
│   ├── start: string                 # Period start date
│   └── end: string                   # Period end date
├── identifier[]                      # External identifiers (claim ID, etc.)
│   ├── system: string                # Identifier system (e.g., CMS)
│   └── value: string                 # Identifier value
├── type                              # Type of claim
│   └── coding[]                      # Claim type codes
│       ├── code: string              # "institutional", "professional", "pharmacy"
│       └── system: string            # Code system URL
├── patient                           # Patient who received services
│   └── reference: string             # Patient resource reference
├── provider                          # Provider who rendered services
│   └── reference: string             # Practitioner resource reference
├── insurer                           # Insurance company
│   └── display: string               # Insurer name (e.g., "Aetna")
├── facility                          # Where services were provided
│   ├── display: string               # Facility name
│   └── reference: string             # Location resource reference
├── claim                             # Original claim this explains
│   └── reference: string             # Claim resource reference
├── referral                          # Referral that authorized services
│   └── reference: string             # ServiceRequest resource reference
├── insurance[]                       # Insurance used
│   ├── focal: boolean                # Is this the primary coverage?
│   └── coverage                      # Coverage details
│       ├── display: string           # Coverage/plan name
│       └── reference: string         # Coverage resource reference
├── careTeam[]                        # Providers involved
│   ├── sequence: integer             # Provider sequence number
│   ├── provider                      # The provider
│   │   └── reference: string         # Practitioner resource reference
│   └── role                          # Provider's role
│       └── coding[]                  # Role codes
│           ├── code: string          # Role code
│           ├── display: string       # Role name (e.g., "Primary", "Supervising")
│           └── system: string        # Code system URL
├── diagnosis[]                       # Diagnoses for this claim
│   ├── sequence: integer             # Diagnosis sequence (1=principal)
│   ├── diagnosisReference            # The diagnosis
│   │   └── reference: string         # Condition resource reference
│   └── type[]                        # Diagnosis type
│       └── coding[]                  # Type codes
│           ├── code: string          # "principal", "admitting", "discharge"
│           └── system: string        # Code system URL
├── item[]                            # Service line items
│   ├── sequence: integer             # Line item number
│   ├── category                      # Service category
│   │   └── coding[]                  # Category codes
│   │       ├── code: string          # Category code
│   │       ├── display: string       # Category name
│   │       └── system: string        # Code system URL
│   ├── productOrService              # What was provided
│   │   ├── text: string              # Service description
│   │   └── coding[]                  # Service codes
│   │       ├── code: string          # CPT/HCPCS code
│   │       ├── display: string       # Service name
│   │       └── system: string        # Code system URL
│   ├── servicedPeriod                # When service was provided
│   │   ├── start: string             # Service start date
│   │   └── end: string               # Service end date
│   ├── locationCodeableConcept       # Place of service
│   │   └── coding[]                  # Location codes
│   │       ├── code: string          # Place of service code
│   │       ├── display: string       # Location type (e.g., "Office", "Hospital")
│   │       └── system: string        # Code system URL
│   └── encounter[]                   # Related encounter
│       └── reference: string         # Encounter resource reference
├── total[]                           # Total amounts
│   ├── amount                        # Dollar amount
│   │   ├── value: float              # Amount (e.g., 500.00)
│   │   └── currency: string          # Currency (e.g., "USD")
│   └── category                      # What this total represents
│       ├── text: string              # Category description
│       └── coding[]                  # Category codes
│           ├── code: string          # "submitted", "benefit", "copay", "deductible"
│           ├── display: string       # Category name
│           └── system: string        # Code system URL
├── payment                           # Payment information
│   └── amount                        # Amount paid
│       ├── value: float              # Payment amount
│       └── currency: string          # Currency (e.g., "USD")
└── contained[]                       # Embedded related resources
    ├── id: string                    # Contained resource ID
    ├── status: string                # Status of contained resource
    ├── intent: string                # Intent (for ServiceRequest)
    ├── subject                       # Subject of contained resource
    │   └── reference: string         # Reference to subject
    ├── requester                     # Who requested (for ServiceRequest)
    │   └── reference: string         # Practitioner reference
    └── performer[]                   # Who performed
        └── reference: string         # Practitioner reference
```

---

## 12. ImagingStudy

Imaging procedures (X-rays, MRIs, CT scans, etc.).

```
ImagingStudy
├── id: string                        # Unique identifier for this imaging study
├── status: string                    # "registered", "available", "cancelled", "entered-in-error"
├── started: string                   # When the study began (ISO 8601)
├── numberOfSeries: integer           # Total number of image series in study
├── numberOfInstances: integer        # Total number of individual images
├── identifier[]                      # Study identifiers (accession number, etc.)
│   ├── use: string                   # "usual", "official", "temp"
│   ├── system: string                # Identifier system (e.g., DICOM UID)
│   └── value: string                 # Study instance UID or accession number
├── subject                           # Patient who was imaged
│   └── reference: string             # Patient resource reference
├── encounter                         # Encounter where imaging was ordered
│   └── reference: string             # Encounter resource reference
├── location                          # Where imaging was performed
│   ├── display: string               # Facility/department name
│   └── reference: string             # Location resource reference
├── procedureCode[]                   # What imaging procedure was done
│   ├── text: string                  # Human-readable procedure (e.g., "Chest X-Ray 2 Views")
│   └── coding[]                      # Procedure codes
│       ├── code: string              # CPT/SNOMED code for the procedure
│       ├── display: string           # Procedure name
│       └── system: string            # Code system URL
└── series[]                          # Individual image series
    ├── uid: string                   # DICOM Series Instance UID
    ├── number: integer               # Series number within the study
    ├── numberOfInstances: integer    # Number of images in this series
    ├── started: string               # When this series was acquired (ISO 8601)
    ├── modality                      # Imaging modality used
    │   ├── code: string              # Modality code (e.g., "CR", "MR", "CT")
    │   ├── display: string           # Modality name (e.g., "Computed Radiography")
    │   └── system: string            # DICOM modality code system
    ├── bodySite                      # Body part imaged
    │   ├── code: string              # SNOMED body site code
    │   ├── display: string           # Body part name (e.g., "Chest", "Left Knee")
    │   └── system: string            # Code system URL
    └── instance[]                    # Individual images in the series
        ├── uid: string               # DICOM SOP Instance UID
        ├── number: integer           # Image number within series
        ├── title: string             # Image title/description
        └── sopClass                  # DICOM SOP Class
            ├── code: string          # SOP Class UID
            └── system: string        # DICOM UID system
```

---

## 13. Immunization

Vaccination and immunization records.

```
Immunization
├── id: string                        # Unique identifier for this immunization record
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "completed", "entered-in-error", "not-done"
├── primarySource: boolean            # Was this reported by the administering provider?
├── occurrenceDateTime: string        # When the vaccine was given (ISO 8601)
├── vaccineCode                       # What vaccine was administered
│   ├── text: string                  # Human-readable vaccine name (e.g., "Influenza Vaccine")
│   └── coding[]                      # Vaccine codes
│       ├── code: string              # CVX code for the vaccine
│       ├── display: string           # Vaccine name
│       └── system: string            # Code system (CVX)
├── patient                           # Patient who received the vaccine
│   └── reference: string             # Patient resource reference
├── encounter                         # Encounter when vaccine was given
│   └── reference: string             # Encounter resource reference
└── location                          # Where the vaccine was administered
    ├── display: string               # Facility name
    └── reference: string             # Location resource reference
```

---

## 14. Medication

Medication definitions and details.

```
Medication
├── id: string                        # Unique identifier for this medication
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "active", "inactive", "entered-in-error"
└── code                              # What medication this is
    ├── text: string                  # Human-readable medication name (e.g., "Lisinopril 10mg")
    └── coding[]                      # Medication codes
        ├── code: string              # RxNorm code for the medication
        ├── display: string           # Medication name
        └── system: string            # Code system (usually RxNorm)
```

---

## 15. MedicationAdministration

Records of medications administered to patients.

```
MedicationAdministration
├── id: string                        # Unique identifier for this administration record
├── status: string                    # "in-progress", "completed", "stopped", "not-done"
├── effectiveDateTime: string         # When medication was given (ISO 8601)
├── medicationCodeableConcept         # What medication was administered
│   ├── text: string                  # Human-readable medication name
│   └── coding[]                      # Medication codes
│       ├── code: string              # RxNorm code
│       ├── display: string           # Medication name
│       └── system: string            # Code system (RxNorm)
├── subject                           # Patient who received the medication
│   └── reference: string             # Patient resource reference
├── context                           # Encounter during which it was given
│   └── reference: string             # Encounter resource reference
├── reasonCode[]                      # Why medication was given
│   ├── text: string                  # Human-readable reason
│   └── coding[]                      # Reason codes
│       ├── code: string              # Indication code (SNOMED)
│       ├── display: string           # Reason description
│       └── system: string            # Code system URL
├── reasonReference[]                 # Condition that justified administration
│   ├── display: string               # Condition name
│   └── reference: string             # Condition resource reference
└── dosage                            # Dose information
    ├── dose                          # Amount given
    │   └── value: float              # Dose quantity (e.g., 500 for 500mg)
    └── rateQuantity                  # Rate of administration
        └── value: integer            # Rate value (for IV infusions)
```

---

## 16. MedicationRequest

Prescriptions and medication orders.

```
MedicationRequest
├── id: string                        # Unique identifier for this prescription
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "active", "completed", "cancelled", "stopped"
├── intent: string                    # "proposal", "plan", "order", "reflex-order"
├── authoredOn: string                # When prescription was written (ISO 8601)
├── medicationCodeableConcept         # What medication is prescribed
│   ├── text: string                  # Human-readable medication name
│   └── coding[]                      # Medication codes
│       ├── code: string              # RxNorm code
│       ├── display: string           # Medication name (e.g., "Metformin 500mg tablet")
│       └── system: string            # Code system (RxNorm)
├── medicationReference               # Reference to Medication resource (alternative)
│   └── reference: string             # Medication resource reference
├── subject                           # Patient the prescription is for
│   └── reference: string             # Patient resource reference
├── encounter                         # Encounter when prescribed
│   └── reference: string             # Encounter resource reference
├── requester                         # Prescribing provider
│   ├── display: string               # Provider name
│   └── reference: string             # Practitioner resource reference
├── category[]                        # Medication category
│   ├── text: string                  # Category description
│   └── coding[]                      # Category codes
│       ├── code: string              # "inpatient", "outpatient", "community"
│       ├── display: string           # Category name
│       └── system: string            # Code system URL
├── reasonCode[]                      # Why medication is prescribed
│   ├── text: string                  # Human-readable indication
│   └── coding[]                      # Indication codes
│       ├── code: string              # SNOMED/ICD code for indication
│       ├── display: string           # Indication name
│       └── system: string            # Code system URL
├── reasonReference[]                 # Condition being treated
│   ├── display: string               # Condition name
│   └── reference: string             # Condition resource reference
└── dosageInstruction[]               # How to take the medication
    ├── sequence: integer             # Order of instructions
    ├── text: string                  # Full sig text (e.g., "Take 1 tablet by mouth twice daily")
    ├── asNeededBoolean: boolean      # Is this PRN (as needed)?
    ├── timing                        # When to take
    │   └── repeat                    # Repeat schedule
    │       ├── frequency: integer    # Times per period (e.g., 2 for twice)
    │       ├── period: float         # Period value (e.g., 1)
    │       └── periodUnit: string    # Period unit ("d"=day, "h"=hour, "wk"=week)
    ├── additionalInstruction[]       # Extra instructions
    │   ├── text: string              # Instruction text (e.g., "Take with food")
    │   └── coding[]                  # Instruction codes
    │       ├── code: string          # SNOMED instruction code
    │       ├── display: string       # Instruction text
    │       └── system: string        # Code system URL
    └── doseAndRate[]                 # Dose amount
        ├── doseQuantity              # Dose per administration
        │   └── value: float          # Dose value (e.g., 1 tablet, 500 mg)
        └── type                      # Type of dose
            └── coding[]              # Dose type codes
                ├── code: string      # "ordered", "calculated"
                ├── display: string   # Type name
                └── system: string    # Code system URL
```

---

## 17. Observation

Clinical observations including vital signs, lab results, and assessments.

```
Observation
├── id: string                        # Unique identifier for this observation
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "registered", "preliminary", "final", "amended"
├── effectiveDateTime: string         # When observation was made (ISO 8601)
├── issued: string                    # When result was released (ISO 8601)
├── category[]                        # Classification of observation type
│   └── coding[]                      # Category codes
│       ├── code: string              # "vital-signs", "laboratory", "survey", etc.
│       ├── display: string           # Category name
│       └── system: string            # Code system URL
├── code                              # What was observed
│   ├── text: string                  # Human-readable name (e.g., "Blood Pressure")
│   └── coding[]                      # Observation codes
│       ├── code: string              # LOINC code for the observation
│       ├── display: string           # Observation name
│       └── system: string            # Code system (usually LOINC)
├── subject                           # Patient observed
│   └── reference: string             # Patient resource reference
├── encounter                         # Encounter when observed
│   └── reference: string             # Encounter resource reference
├── valueQuantity                     # Numeric result value
│   ├── value: float                  # The measurement (e.g., 120)
│   ├── unit: string                  # Human-readable unit (e.g., "mmHg")
│   ├── code: string                  # UCUM unit code
│   └── system: string                # UCUM system URL
├── valueCodeableConcept              # Coded result (for non-numeric observations)
│   ├── text: string                  # Human-readable result
│   └── coding[]                      # Result codes
│       ├── code: string              # Result code (e.g., "positive", "negative")
│       ├── display: string           # Result display text
│       └── system: string            # Code system URL
└── component[]                       # Sub-observations (e.g., systolic/diastolic BP)
    ├── code                          # What component this is
    │   ├── text: string              # Component name (e.g., "Systolic Blood Pressure")
    │   └── coding[]                  # Component codes
    │       ├── code: string          # LOINC code for component
    │       ├── display: string       # Component name
    │       └── system: string        # Code system URL
    ├── valueQuantity                 # Numeric component value
    │   ├── value: integer            # The measurement
    │   ├── unit: string              # Unit of measure
    │   ├── code: string              # UCUM code
    │   └── system: string            # UCUM system URL
    └── valueCodeableConcept          # Coded component value
        ├── text: string              # Human-readable value
        └── coding[]                  # Value codes
            ├── code: string          # Result code
            ├── display: string       # Display text
            └── system: string        # Code system URL
```

---

## 18. Patient

Patient demographic and administrative information.

```
Patient
├── id: string                        # Unique identifier for this patient
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to (e.g., US Core Patient)
├── gender: string                    # "male", "female", "other", "unknown"
├── birthDate: string                 # Date of birth (YYYY-MM-DD)
├── deceasedDateTime: string          # Date/time of death if applicable (ISO 8601)
├── multipleBirthBoolean: boolean     # Is patient part of a multiple birth?
├── text                              # Human-readable summary
│   ├── div: string                   # XHTML content
│   └── status: string                # "generated", "extensions", "additional"
├── identifier[]                      # Patient identifiers (MRN, SSN, etc.)
│   ├── system: string                # Identifier system (e.g., hospital MRN system)
│   └── value: string                 # Identifier value (e.g., MRN number)
├── name[]                            # Patient names
│   ├── use: string                   # "official", "usual", "nickname", "maiden"
│   ├── family: string                # Family/last name
│   ├── given[]: string               # Given/first names (array for middle names)
│   └── prefix[]: string              # Prefixes (e.g., "Mr.", "Dr.")
├── telecom[]                         # Contact information
│   ├── system: string                # "phone", "email", "fax", "sms"
│   ├── use: string                   # "home", "work", "mobile"
│   └── value: string                 # Phone number or email address
├── address[]                         # Patient addresses
│   ├── city: string                  # City name
│   ├── state: string                 # State/province
│   ├── postalCode: string            # ZIP/postal code
│   ├── country: string               # Country code
│   ├── line[]: string                # Street address lines
│   └── extension[]                   # Address extensions (geolocation, etc.)
│       ├── url: string               # Extension URL
│       └── extension[]               # Nested extensions
│           ├── url: string           # Sub-extension URL (e.g., "latitude")
│           └── valueDecimal: float   # Coordinate value
├── maritalStatus                     # Marital status
│   ├── text: string                  # Human-readable status
│   └── coding[]                      # Status codes
│       ├── code: string              # "S" (single), "M" (married), "D" (divorced), etc.
│       ├── display: string           # Status name
│       └── system: string            # Code system URL
├── communication[]                   # Languages the patient speaks
│   └── language                      # Language details
│       ├── text: string              # Language name
│       └── coding[]                  # Language codes
│           ├── code: string          # Language code (e.g., "en", "es")
│           ├── display: string       # Language name (e.g., "English")
│           └── system: string        # Code system URL
└── extension[]                       # Additional patient information
    ├── url: string                   # Extension URL (e.g., race, ethnicity)
    └── extension[]                   # Nested extension values
        ├── url: string               # Sub-extension URL
        └── valueCoding               # Coded value
            ├── code: string          # Code (e.g., race code)
            ├── display: string       # Display value (e.g., "White", "Hispanic")
            └── system: string        # Code system URL
```

---

## 19. Procedure

Clinical procedures performed on patients.

```
Procedure
├── id: string                        # Unique identifier for this procedure record
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── status: string                    # "preparation", "in-progress", "completed", "stopped"
├── performedPeriod                   # When the procedure was done
│   ├── start: string                 # Procedure start time (ISO 8601)
│   └── end: string                   # Procedure end time (ISO 8601)
├── code                              # What procedure was performed
│   ├── text: string                  # Human-readable procedure name (e.g., "Appendectomy")
│   └── coding[]                      # Procedure codes
│       ├── code: string              # CPT/SNOMED code for the procedure
│       ├── display: string           # Procedure name
│       └── system: string            # Code system (CPT, SNOMED CT)
├── subject                           # Patient who had the procedure
│   └── reference: string             # Patient resource reference
├── encounter                         # Encounter during which procedure was done
│   └── reference: string             # Encounter resource reference
├── location                          # Where procedure was performed
│   ├── display: string               # Location/facility name
│   └── reference: string             # Location resource reference
├── reasonCode[]                      # Why procedure was needed
│   ├── text: string                  # Human-readable reason
│   └── coding[]                      # Reason codes
│       ├── code: string              # ICD-10/SNOMED code for indication
│       ├── display: string           # Reason description
│       └── system: string            # Code system URL
└── reasonReference[]                 # Condition that required the procedure
    ├── display: string               # Condition name
    └── reference: string             # Condition resource reference
```

---

## 20. Provenance

Audit trail and data origin tracking.

```
Provenance
├── id: string                        # Unique identifier for this provenance record
├── meta                              # Metadata about this resource
│   └── profile[]: string             # FHIR profiles this conforms to
├── recorded: string                  # When this provenance was recorded (ISO 8601)
├── target[]                          # Resources this provenance applies to
│   └── reference: string             # Reference to the target resource
└── agent[]                           # Who/what was involved in creating the data
    ├── type                          # What role the agent played
    │   ├── text: string              # Human-readable role description
    │   └── coding[]                  # Role codes
    │       ├── code: string          # Agent role code (e.g., "author", "performer")
    │       ├── display: string       # Role name
    │       └── system: string        # Code system URL
    ├── who                           # The actual agent (person/device/organization)
    │   ├── display: string           # Agent name
    │   └── reference: string         # Practitioner/Device/Organization reference
    └── onBehalfOf                    # Organization the agent was acting for
        ├── display: string           # Organization name
        └── reference: string         # Organization resource reference
```

---

## 21. ServiceRequest

Orders and requests for healthcare services.

```
ServiceRequest
├── id: string                        # Unique identifier for this request
├── status: string                    # "draft", "active", "completed", "cancelled"
├── intent: string                    # "proposal", "plan", "order", "reflex-order"
├── subject                           # Patient the service is for
│   └── reference: string             # Patient resource reference
├── requester                         # Who ordered the service
│   └── reference: string             # Practitioner resource reference
└── performer[]                       # Who should perform the service
    └── reference: string             # Practitioner/Organization reference
```

---

## 22. SupplyDelivery

Medical supply delivery records.

```
SupplyDelivery
├── id: string                        # Unique identifier for this delivery record
├── status: string                    # "in-progress", "completed", "abandoned"
├── occurrenceDateTime: string        # When delivery occurred (ISO 8601)
├── patient                           # Patient receiving the supplies
│   └── reference: string             # Patient resource reference
├── type                              # Category of supply delivered
│   └── coding[]                      # Supply type codes
│       ├── code: string              # Supply category code
│       ├── display: string           # Category name (e.g., "medication", "device")
│       └── system: string            # Code system URL
└── suppliedItem                      # What was delivered
    ├── quantity                      # Amount delivered
    │   └── value: integer            # Quantity value
    └── itemCodeableConcept           # The actual item
        ├── text: string              # Human-readable item name
        └── coding[]                  # Item codes
            ├── code: string          # Product code (SNOMED, NDC, etc.)
            ├── display: string       # Item name
            └── system: string        # Code system URL
```

---

## Summary

| # | Resource Type | Primary Use |
|---|---------------|-------------|
| 1 | AllergyIntolerance | Patient allergies and adverse reactions |
| 2 | CarePlan | Treatment and care management plans |
| 3 | CareTeam | Healthcare provider teams |
| 4 | Claim | Insurance billing claims |
| 5 | Condition | Diagnoses and health problems |
| 6 | Coverage | Insurance coverage details |
| 7 | Device | Medical devices and implants |
| 8 | DiagnosticReport | Lab and diagnostic test results |
| 9 | DocumentReference | Clinical document references |
| 10 | Encounter | Patient visits and interactions |
| 11 | ExplanationOfBenefit | Insurance benefit details |
| 12 | ImagingStudy | Radiology and imaging data |
| 13 | Immunization | Vaccination records |
| 14 | Medication | Medication definitions |
| 15 | MedicationAdministration | Medication given records |
| 16 | MedicationRequest | Prescriptions and orders |
| 17 | Observation | Vitals, labs, and clinical findings |
| 18 | Patient | Patient demographics |
| 19 | Procedure | Clinical procedures performed |
| 20 | Provenance | Data audit trail |
| 21 | ServiceRequest | Service orders and referrals |
| 22 | SupplyDelivery | Medical supply deliveries |

---

*Generated from FHIR data in `xtdb-fhir/data/fhir/` folder - Synthea synthetic patient data*
