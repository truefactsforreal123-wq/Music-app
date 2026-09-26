# Publishes the Aura server to the VM and provisions it.
# Usage:
#   .\deploy\publish.ps1 -VmIp 129.146.1.23 -SshUser ubuntu -KeyPath C:\Users\you\.ssh\id_ed25519
param(
  [Parameter(Mandatory = $true)][string]$VmIp,
  [string]$SshUser = 'ubuntu',
  [Parameter(Mandatory = $true)][string]$KeyPath,
  [string]$SshPort = 22
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$server = Join-Path $root 'server'
$ssh = @('-i', $KeyPath, '-p', $SshPort, '-o', 'StrictHostKeyChecking=accept-new', "$SshUser@$VmIp")
$scp = @('-i', $KeyPath, '-P', $SshPort, '-o', 'StrictHostKeyChecking=accept-new')

$stage = Join-Path $env:TEMP "aura-deploy"
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage | Out-Null
New-Item -ItemType Directory -Path "$stage\server" | Out-Null

# Copy server sources, excluding runtime junk.
$exclude = @('node_modules', 'data', 'bin', 'work', '*.log', '*.db', '*.db-shm', '*.db-wal')
Copy-Item "$server\src", "$server\scripts", "$server\package.json", "$server\package-lock.json", "$server\tsconfig.json" `
  -Destination "$stage\server" -Recurse -ErrorAction SilentlyContinue
if (Test-Path "$server\.env") { Copy-Item "$server\.env" "$stage\server\.env" -Recurse }

tar -czf "$stage\server.tar.gz" -C "$stage" server
Write-Host "staged $((Get-Item "$stage\server.tar.gz").Length / 1KB) KB"

Write-Host "uploading..."
& scp @scp "$stage\server.tar.gz" "$SshUser@${VmIp}:/tmp/aura-server.tar.gz"
& scp @scp "$PSScriptRoot\bootstrap-vm.sh" "$SshUser@${VmIp}:/tmp/bootstrap-vm.sh"

Write-Host "provisioning (sudo)..."
$cmd = 'set -e; rm -rf /opt/aura/server; mkdir -p /opt/aura/server; tar -xzf /tmp/aura-server.tar.gz -C /opt/aura/server --strip-components=1; chmod +x /tmp/bootstrap-vm.sh; sudo bash /tmp/bootstrap-vm.sh'
& ssh @ssh $cmd
if ($LASTEXITCODE -ne 0) { throw "remote provisioning failed ($LASTEXITCODE)" }

Write-Host "`nDone. In Aura app, Settings -> Server:  http://${VmIp}:8787"
