$testFile = "F:\Projects\Real-time-Chat-Application\test-35vu-runtime-metrics.csv"
"timestamp,cpu_pct,mem_usage_mb,pids,net_in_mb,net_out_mb,jvm_threads,jvm_rss_kb,jvm_fds,pg_active,pg_idle,pg_waiting" | Out-File -FilePath $testFile -Encoding UTF8

for ($i = 1; $i -le 90; $i++) {
    $ts = Get-Date -Format "HH:mm:ss"
    
    # Docker stats
    $dockerLine = docker stats blink-message-service --no-stream --format "{{.CPUPerc}},{{.MemUsage}},{{.PIDs}},{{.NetIO}}" 2>$null
    $cpu = ($dockerLine -split ',')[0] -replace '%',''
    $memRaw = ($dockerLine -split ',')[1] -split '/' | Select-Object -First 1
    $memMb = [math]::Round(([regex]::Match($memRaw, '[\d.]+').Value -as [double]) * ($memRaw -match 'GiB' ? 1024 : 1), 1)
    $pids = ($dockerLine -split ',')[2].Trim()
    $netParts = ($dockerLine -split ',')[3] -split '/'
    $netIn = [math]::Round(([regex]::Match($netParts[0], '[\d.]+').Value -as [double]) * ($netParts[0] -match 'GB' ? 1024 : ($netParts[0] -match 'MB' ? 1 : 0.001)), 2)
    $netOut = [math]::Round(([regex]::Match($netParts[1].Trim(), '[\d.]+').Value -as [double]) * ($netParts[1] -match 'GB' ? 1024 : ($netParts[1] -match 'MB' ? 1 : 0.001)), 2)
    
    # JVM metrics
    $jvmInfo = docker exec blink-message-service sh -c "cat /proc/1/status | grep -E 'Threads|VmRSS|FDSize'" 2>$null
    $jvmThreads = ([regex]::Match($jvmInfo, 'Threads:\s+(\d+)')).Groups[1].Value
    $jvmRss = ([regex]::Match($jvmInfo, 'VmRSS:\s+(\d+)')).Groups[1].Value
    $jvmFds = ([regex]::Match($jvmInfo, 'FDSize:\s+(\d+)')).Groups[1].Value
    
    # PostgreSQL
    $pgInfo = docker exec blink-postgres psql -U blink_user -d blink_db -t -c "SELECT state, count(*) FROM pg_stat_activity WHERE datname = 'blink_db' GROUP BY state;" 2>$null
    $pgActive = 0; $pgIdle = 0; $pgWaiting = 0
    $pgLines = $pgInfo -split "`n" | Where-Object { $_ -match '\S' }
    foreach ($line in $pgLines) {
        $line = $line.Trim()
        if ($line -match 'active') { $pgActive = ([regex]::Match($line, '(\d+)\s*$')).Groups[1].Value }
        elseif ($line -match 'idle') { $pgIdle = ([regex]::Match($line, '(\d+)\s*$')).Groups[1].Value }
        elseif ($line -match 'waiting') { $pgWaiting = ([regex]::Match($line, '(\d+)\s*$')).Groups[1].Value }
    }
    
    "$ts,$cpu,$memMb,$pids,$netIn,$netOut,$jvmThreads,$jvmRss,$jvmFds,$pgActive,$pgIdle,$pgWaiting" | Out-File -FilePath $testFile -Append -Encoding UTF8
    
    Start-Sleep -Seconds 5
}
