# scripts/eval-models.ps1
# One-time model comparison for PersonaVault RAG quality + speed.
# Usage: .\scripts\eval-models.ps1
# Requires: Ollama running with both models pulled.

$ErrorActionPreference = "Stop"

$models = @("llama3.1:8b", "qwen2.5:3b")

$systemPrompt = @"
You are a personal assistant with access to the user's documents
(insurance policies, financial records, medical history).
Only derive information from the provided context.
If the answer is not in the context, say so explicitly.
Never fabricate dates, amounts, or policy details.
Cite the source document when providing information.
"@

$context = @"
Context from user's documents:

[Document: health_policy_2025.txt]
HEALTH INSURANCE POLICY
Policy Number: HP-2025-00123
Insurer: HDFC Ergo General Insurance
Policy Type: Health (Family Floater)
Sum Insured: 10,00,000 INR
Annual Premium: 15,000 INR
Premium Frequency: Annual
Effective Date: 15/03/2025
Expiry Date: 14/03/2026

[Document: car_policy_2025.txt]
CAR INSURANCE POLICY
Policy Number: CV-2025-00456
Insurer: ICICI Lombard General Insurance
Policy Type: Motor (Comprehensive)
Sum Insured: 5,00,000 INR
Annual Premium: 8,500 INR
Premium Frequency: Annual
Effective Date: 01/11/2025
Expiry Date: 31/10/2026
"@

$questions = @(
@{ q = "What is the policy number of my health insurance?"; expected = "HP-2025-00123" },
@{ q = "What is my annual car insurance premium?"; expected = "8,500" },
@{ q = "When does my health policy expire?"; expected = "14/03/2026" },
@{ q = "Which insurer covers my car?"; expected = "ICICI Lombard" },
@{ q = "What is the capital of Australia?"; expected = @("don't have", "do not have", "not in", "not mention", "does not mention", "cannot find", "no information", "not provided", "not available") }
)

Write-Host ""
Write-Host "  PersonaVault - Model A/B Evaluation" -ForegroundColor Cyan
Write-Host "  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
Write-Host ("  " + ("=" * 72))

$results = @{}

foreach ($model in $models) {
    Write-Host ""
    Write-Host "  Model: $model" -ForegroundColor Green
    Write-Host ("  " + ("-" * 72))

    $results[$model] = @()

    foreach ($item in $questions) {
        $q = $item.q
        $expected = $item.expected

        $body = @{
            model  = $model
            stream = $false
            messages = @(
            @{ role = "system"; content = "$systemPrompt`n`n$context" },
            @{ role = "user";   content = $q }
            )
        } | ConvertTo-Json -Depth 5

        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:11434/api/chat" `
                -Method POST -Body $body -ContentType "application/json"
            $sw.Stop()
        } catch {
            $sw.Stop()
            Write-Host "  Q: $q"
            Write-Host "  ERROR: $_" -ForegroundColor Red
            $results[$model] += @{ Q = $q; Answer = "ERROR"; Ms = $sw.ElapsedMilliseconds; Pass = $false }
            continue
        }

        $answer = $resp.message.content
        $tokens = $resp.eval_count
        $speed = 0
        if ($sw.ElapsedMilliseconds -gt 0) {
            $speed = [math]::Round($tokens / ($sw.ElapsedMilliseconds / 1000), 1)
        }

        $pass = $answer.ToLower().Contains($expected.ToLower())
        $status = "FAIL"
        $color = "Red"
        if ($pass) {
            $status = "PASS"
            $color = "Green"
        }

        Write-Host "  Q: $q"
        Write-Host "  A: $answer"
        Write-Host ("  [" + $status + "  " + $sw.ElapsedMilliseconds + "ms  " + $tokens + " tokens  " + $speed + " tok/s]") -ForegroundColor $color
        Write-Host ""

        $results[$model] += @{ Q = $q; Answer = $answer; Ms = $sw.ElapsedMilliseconds; Tokens = $tokens; Speed = $speed; Pass = $pass }
    }
}

Write-Host ""
Write-Host ("  " + ("=" * 72)) -ForegroundColor Cyan
Write-Host "  SUMMARY" -ForegroundColor Cyan
Write-Host ("  " + ("=" * 72))

foreach ($model in $models) {
    $r = $results[$model]
    $passed = ($r | Where-Object { $_.Pass }).Count
    $total = $r.Count
    $avgMs = [math]::Round(($r | ForEach-Object { $_.Ms } | Measure-Object -Average).Average)
    $speeds = $r | ForEach-Object { $_.Speed } | Where-Object { $_ -gt 0 }
    $avgSpeed = 0
    if ($speeds.Count -gt 0) {
        $avgSpeed = [math]::Round(($speeds | Measure-Object -Average).Average, 1)
    }

    Write-Host ""
    Write-Host ("  " + $model + " : " + $passed + "/" + $total + " correct  avg " + $avgMs + "ms  avg " + $avgSpeed + " tok/s")
}

Write-Host ""
Write-Host "  Decision: if qwen2.5:3b scores 5/5 AND is 2x+ faster," -ForegroundColor Yellow
Write-Host "  switch OLLAMA_CHAT_MODEL to qwen2.5:3b in application.yml." -ForegroundColor Yellow
Write-Host ""