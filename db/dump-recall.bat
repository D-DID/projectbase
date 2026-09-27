@echo off
REM ==========================================================================
REM  Recall data dump  -  run this only if you loaded the data (admin).
REM  Teammates: use load-recall.bat instead.
REM  Output: recall-dump.sql  in this folder.
REM  (Korean notes are in README.md - batch files stay ASCII on purpose.)
REM ==========================================================================
setlocal enabledelayedexpansion

set "DB_NAME=recallcheck"
set "DB_USER=root"
set "OUT=%~dp0recall-dump.sql"
set "DUMPEXE="

echo.
echo === Looking for mysqldump.exe ===

REM 1) on PATH
for /f "delims=" %%P in ('where mysqldump 2^>nul') do if not defined DUMPEXE set "DUMPEXE=%%P"

REM 2) common install locations
if not defined DUMPEXE (
  for %%D in (
    "C:\Program Files\MySQL\MySQL Server 8.0\bin"
    "C:\Program Files\MySQL\MySQL Server 8.4\bin"
    "C:\Program Files\MySQL\MySQL Workbench 8.0 CE"
    "C:\Program Files\MySQL\MySQL Shell 8.0\bin"
    "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin"
    "C:\xampp\mysql\bin"
    "C:\Bitnami\mysql\bin"
  ) do if not defined DUMPEXE if exist "%%~D\mysqldump.exe" set "DUMPEXE=%%~D\mysqldump.exe"
)

if not defined DUMPEXE (
  echo.
  echo [ERROR] mysqldump.exe not found.
  echo.
  echo Either MySQL client tools are not installed, or they are somewhere else.
  echo Find it yourself with this in CMD:
  echo     dir /s /b "C:\Program Files\mysqldump.exe"
  echo Then set DUMPEXE by hand near the top of this file.
  echo.
  echo If it really is missing, use the IntelliJ export route in README.md.
  echo.
  pause
  exit /b 1
)

echo Found: !DUMPEXE!
echo.
echo === Dumping %DB_NAME% ^(tables: recall, recall_file^) ===
echo Enter the MySQL password when asked.
echo.

"!DUMPEXE!" -u %DB_USER% -p --default-character-set=utf8mb4 --single-transaction --add-drop-table --no-tablespaces --skip-comments %DB_NAME% recall recall_file > "%OUT%"

if errorlevel 1 (
  echo.
  echo [ERROR] Dump failed. See the message above.
  echo.
  pause
  exit /b 1
)

for %%A in ("%OUT%") do set "SZ=%%~zA"
echo.
echo Done: %OUT%
echo Size: !SZ! bytes
echo.
if !SZ! LSS 100000 (
  echo [WARN] That looks too small for 778 rows. Expected roughly 1-3 MB.
  echo        Open the file and check it is not an error message.
  echo.
)
echo Commit db\recall-dump.sql to git to share it.
echo.
pause
endlocal
