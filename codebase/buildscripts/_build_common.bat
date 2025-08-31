@echo off
rem Usage: call _build_common.bat start|end script_name
if "%1"=="start" (
    set "_start=%time%"
    echo [%2] Start: %date% %time%
) else if "%1"=="end" (
    set "_end=%time%"
    echo [%2] End: %date% %time%
    for /f "tokens=1-4 delims=:." %%a in ("%_start%") do set /a "_startsec=(((%%a*60)+1%%b-100)*60+1%%c-100)*100+1%%d-100"
    for /f "tokens=1-4 delims=:." %%a in ("%_end%") do set /a "_endsec=(((%%a*60)+1%%b-100)*60+1%%c-100)*100+1%%d-100"
    set /a _elapsed=_endsec-_startsec
    set /a _ms=_elapsed%%100
    set /a _elapsed=_elapsed/100
    set /a _s=_elapsed%%60
    set /a _elapsed=_elapsed/60
    set /a _m=_elapsed%%60
    set /a _h=_elapsed/60
    echo [%2] Total elapsed: %_h%h %_m%m %_s%s %_ms%ms
}
