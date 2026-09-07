<#
==========================================================
 校园智能求职与审核平台 —— 一键启动演示脚本
 功能：
   1. 检查 MySQL（3306）
   2. 启动后端（Spring Boot, 8080）
   3. 启动前端（Vite dev, 3000，已配置 /api 代理）
   4. 完成三角色登录（admin / dept1 / student1），
      token 保存到 scripts/.runtime/tokens.json 供演示复用
 用法：
   powershell -ExecutionPolicy Bypass -File scripts\start-demo.ps1          # 启动
   powershell -ExecutionPolicy Bypass -File scripts\start-demo.ps1 -Stop    # 停止
   可选参数：-Password "123456"（种子密码，若你改过库密码请传入）
==========================================================
#>
param(
    [switch]$Stop,
    [string]$Password = "123456"
)

$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot          # 代码/
$backend = Join-Path $root 'backend'
$frontend= Join-Path $root 'frontend'
$runtime = Join-Path $PSScriptRoot '.runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null | Out-Null
$pidFile   = Join-Path $runtime 'pids.json'
$tokenFile = Join-Path $runtime 'tokens.json'
$backLog   = Join-Path $runtime 'backend.log'
$frontLog  = Join-Path $runtime 'frontend.log'

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

# ============ 停止模式 ============
if ($Stop) {
    Write-Step "停止服务"
    # 方式 1：端口反查进程（最可靠，不依赖 PID 记录）
    foreach ($stopPort in @(8080, 3000)) {
        try {
            $conns = Get-NetTCPConnection -LocalPort $stopPort -State Listen -ErrorAction SilentlyContinue
            foreach ($c in $conns) {
                Write-Host "  停止监听端口 $stopPort 的进程 PID $($c.OwningProcess) ..."
                taskkill /PID $c.OwningProcess /T /F 2>$null | Out-Null
            }
        } catch { }
    }
    # 方式 2：PID 记录（方式 1 未覆盖时兜底）
    if (Test-Path $pidFile) {
        $pids = Get-Content $pidFile | ConvertFrom-Json
        foreach ($procId in @($pids.backend, $pids.frontend)) {
            if ($procId) {
                $alive = Get-Process -Id $procId -ErrorAction SilentlyContinue
                if ($alive) {
                    Write-Host "  停止记录中的 PID $procId ..."
                    taskkill /PID $procId /T /F 2>$null | Out-Null
                }
            }
        }
        Remove-Item $pidFile -Force
    }
    Write-Host "  已停止。" -ForegroundColor DarkGray
    exit 0
}

# ============ 1. 检查 MySQL ============
Write-Step "1/5 检查 MySQL (3306)"
$mysqlOk = Test-NetConnection -ComputerName localhost -Port 3306 -WarningAction SilentlyContinue
if (-not $mysqlOk.TcpTestSucceeded) {
    Write-Host "[!] MySQL 未在 3306 端口运行。" -ForegroundColor Yellow
    Write-Host "    请先启动 MySQL：docker compose up -d mysql  或启动本机 MySQL 服务。" -ForegroundColor Yellow
    exit 1
}
Write-Host "  MySQL OK"

# ============ 2. API Key（支持 .env 文件） ============
Write-Step "2/5 检查通义千问 API Key"
$envFile = Join-Path $PSScriptRoot '.env'
if (-not $env:DASHSCOPE_API_KEY -and (Test-Path $envFile)) {
    foreach ($line in [System.IO.File]::ReadAllLines($envFile)) {
        $trimmed = $line.Trim()
        if ($trimmed -match '^DASHSCOPE_API_KEY=(.+)$') {
            $env:DASHSCOPE_API_KEY = $Matches[1].Trim()
            break
        }
    }
}
if (-not $env:DASHSCOPE_API_KEY) {
    Write-Host "[!] 未检测到 DASHSCOPE_API_KEY，将以【无 AI 演示模式】启动：" -ForegroundColor Yellow
    Write-Host "    - 系统核心功能（登录/岗位/申请/审核/通知/统计）全部可用" -ForegroundColor Yellow
    Write-Host "    - AI 能力（推荐/对话/预审/撮合等）会提示不可用（后端已内置降级）" -ForegroundColor Yellow
    Write-Host "    启用 AI：将 key 写入 scripts\.env（DASHSCOPE_API_KEY=sk-xxx）或设置环境变量后重启。" -ForegroundColor Yellow
} else {
    Write-Host "  API Key 已配置（长度 $($env:DASHSCOPE_API_KEY.Length)），AI 功能可用"
}

# ============ 3. 启动后端 ============
Write-Step "3/5 启动后端 (http://localhost:8080)"
$backendProc = $null
$backIsRunning = $false
$port8080 = Test-NetConnection -ComputerName localhost -Port 8080 -WarningAction SilentlyContinue
if ($port8080.TcpTestSucceeded) {
    Write-Host "  8080 已被占用——后端可能已在运行，跳过启动。" -ForegroundColor Yellow
    $backIsRunning = $true
} else {
    # 清理旧日志；若被残留进程占用则改用带时间戳的日志名（不阻断启动）
    try {
        if (Test-Path $backLog) { Remove-Item $backLog -Force -ErrorAction Stop }
    } catch {
        $stamp = Get-Date -Format 'MMdd-HHmmss'
        $backLog = Join-Path $runtime "backend-$stamp.log"
        Write-Host "  旧日志被占用，使用新日志：$(Split-Path $backLog -Leaf)" -ForegroundColor DarkGray
    }
    $backendProc = Start-Process -FilePath "mvn.cmd" `
        -ArgumentList "spring-boot:run" `
        -WorkingDirectory $backend `
        -RedirectStandardOutput $backLog `
        -RedirectStandardError (Join-Path $runtime 'backend-err.log') `
        -WindowStyle Hidden -PassThru
}

if ($backIsRunning) {
    # 已运行：只确认健康
    $backReady = $false
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 2
        try {
            $h = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 3
            if ($h.status -eq 'UP') { $backReady = $true; break }
        } catch { }
    }
    if ($backReady) { Write-Host "  后端已在运行且健康（复用现有实例）" } else { Write-Host "  8080 被占用但健康检查未通过，请检查端口占用进程" -ForegroundColor Yellow }
} else {
    $backReady = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 3
        if ($backendProc.HasExited) { break }
        try {
            $h = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 3
            if ($h.status -eq 'UP') { $backReady = $true; break }
        } catch { }
    }
    if (-not $backReady) {
        Write-Host "[!] 后端启动超时或失败，请查看日志：$backLog" -ForegroundColor Red
        exit 1
    }
    Write-Host "  后端已就绪（PID $($backendProc.Id)）"
}

# ============ 4. 启动前端 ============
Write-Step "4/5 启动前端 (http://localhost:3000)"
$frontendProc = $null
$frontIsRunning = $false
$port3000 = Test-NetConnection -ComputerName localhost -Port 3000 -WarningAction SilentlyContinue
if ($port3000.TcpTestSucceeded) {
    Write-Host "  3000 已被占用——前端可能已在运行，跳过启动。" -ForegroundColor Yellow
    $frontIsRunning = $true
} else {
    try {
        if (Test-Path $frontLog) { Remove-Item $frontLog -Force -ErrorAction Stop }
    } catch {
        $stamp = Get-Date -Format 'MMdd-HHmmss'
        $frontLog = Join-Path $runtime "frontend-$stamp.log"
        Write-Host "  旧日志被占用，使用新日志：$(Split-Path $frontLog -Leaf)" -ForegroundColor DarkGray
    }
    $frontendProc = Start-Process -FilePath "npm.cmd" `
        -ArgumentList "run", "dev" `
        -WorkingDirectory $frontend `
        -RedirectStandardOutput $frontLog `
        -RedirectStandardError (Join-Path $runtime 'frontend-err.log') `
        -WindowStyle Hidden -PassThru
}

if ($frontIsRunning) {
    Write-Host "  前端已在运行（复用现有实例）"
} else {
    $frontReady = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        if ($frontendProc.HasExited) { break }
        $t = Test-NetConnection -ComputerName localhost -Port 3000 -WarningAction SilentlyContinue
        if ($t.TcpTestSucceeded) { $frontReady = $true; break }
    }
    if (-not $frontReady) {
        Write-Host "[!] 前端启动超时，请查看日志：$frontLog" -ForegroundColor Yellow
    } else {
        Write-Host "  前端已就绪（PID $($frontendProc.Id)）"
    }
}

# 记录 PID（合并：本次新启动的记录新 PID；已运行复用的保留原记录，避免 stop 丢失）
$existing = @{ }
if (Test-Path $pidFile) {
    try { $existing = Get-Content $pidFile | ConvertFrom-Json } catch { }
}
$pidsToRecord = @{ }
$pidsToRecord.backend  = if ($backendProc)  { $backendProc.Id }  elseif ($existing.backend)  { $existing.backend }  else { 0 }
$pidsToRecord.frontend = if ($frontendProc) { $frontendProc.Id } elseif ($existing.frontend) { $existing.frontend } else { 0 }
$pidsToRecord | ConvertTo-Json | Set-Content $pidFile

# ============ 5. 三角色登录 ============
Write-Step "5/5 三角色登录（密码：$Password）"
$tokens = @{}
foreach ($u in @('admin', 'dept1', 'student1')) {
    try {
        $resp = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post `
            -ContentType 'application/json' `
            -Body (@{ username = $u; password = $Password } | ConvertTo-Json) `
            -TimeoutSec 10
        if ($resp.code -eq 200) {
            $tokens[$u] = $resp.data.token
            $role = switch ($u) { 'admin' { '超级管理员' } 'dept1' { '部门管理员' } 'student1' { '学生' } }
            Write-Host "  [$u] $role 登录成功 ✓" -ForegroundColor Green
        } else {
            Write-Host "  [$u] 登录失败：$($resp.message)（密码可能不是 $Password，可用 -Password 参数指定）" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  [$u] 登录异常：$($_.Exception.Message)" -ForegroundColor Yellow
    }
}
$tokens | ConvertTo-Json | Set-Content $tokenFile
Write-Host "  Token 已保存：$tokenFile"

# ============ 6. 打开三个角色页面（已登录，浏览器自动跳转） ============
if ($tokens.Count -gt 0 -and -not $env:DISABLE_AUTO_OPEN) {
    Write-Step "6 打开三个角色页面（浏览器自动登录）"
    $frontUrl = 'http://localhost:3000'
    $pw = [uri]::EscapeDataString($Password)
    $openTargets = @(
        @{ u = 'admin';    p = "admin" },
        @{ u = 'dept1';    p = "dept1" },
        @{ u = 'student1'; p = "student1" }
    )
    foreach ($t in $openTargets) {
        $url = "$frontUrl/auto-login.html?username=$($t.u)&password=$pw"
        Write-Host "  打开 $($t.u) 页面 ..."
        Start-Process $url
        Start-Sleep -Milliseconds 900
    }
    Write-Host "  已在浏览器打开 3 个标签页：管理员 / 部门管理员 / 学生（各自已登录）" -ForegroundColor Green
} elseif (-not $env:DISABLE_AUTO_OPEN) {
    Write-Host "[!] 登录未全部成功，跳过自动打开浏览器（可手动访问 $frontUrl/auto-login.html?username=xxx&password=xxx）" -ForegroundColor Yellow
}

# ============ 完成 ============
Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "  启动完成！" -ForegroundColor Green
Write-Host "  前端：  http://localhost:3000  （登录后学生端右下角有 AI 求职助手）"
Write-Host "  后端：  http://localhost:8080  （Knife4j 文档 /doc.html）"
Write-Host "  账号：  admin / dept1 / student1  密码：$Password"
Write-Host "  Token： $tokenFile"
Write-Host "  日志：  $backLog / $frontLog"
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  AI 功能需要 DASHSCOPE_API_KEY（可在 scripts\.env 配置）。" -ForegroundColor DarkGray
Write-Host "  停止：powershell -File scripts\start-demo.ps1 -Stop" -ForegroundColor DarkGray
