ui = false
disable_mlock = true

api_addr = "http://vault:8200"
cluster_addr = "http://vault:8201"

storage "raft" {
  path = "/vault/data"
  node_id = "vault-dev-1"
}

listener "tcp" {
  address = "0.0.0.0:8200"
  cluster_address = "0.0.0.0:8201"
  tls_disable = 1
}

telemetry {
  disable_hostname = true
  prometheus_retention_time = "24h"
}
