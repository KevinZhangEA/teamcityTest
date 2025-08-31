@echo off
setlocal
for %%F in (%~nx0) do set "_script_name=%%~nF"
call "%~dp0_build_common.bat" start %_script_name%
call "%~dp0env.bat"

rem ...existing code...

call "%~dp0_build_common.bat" end %_script_name%
endlocal
