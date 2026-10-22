@echo off
setlocal
cd /d %~dp0\..
call scripts\build.bat || exit /b 1
java -jar build\agsa-pppum.jar selftest || exit /b 1
java -jar build\agsa-pppum.jar benchmark --db data\example_db.txt --profits data\example_profits.txt --sensitive data\example_sensitive.txt --minUtil 260 --minPer 1 --maxPer 6 --minAvg 1 --maxAvg 2 --algorithms MU-MAP,MU-MIP,AGSA,FAGSA --topK 5 --out results\example.csv
