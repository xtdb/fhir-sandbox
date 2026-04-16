# Installs VolumeSnapshot CRDs + controller (silences EBS CSI snapshot errors)
resource "aws_eks_addon" "snapshot_controller" {
  cluster_name = module.xtdb_eks.cluster_name
  addon_name   = "snapshot-controller"
}