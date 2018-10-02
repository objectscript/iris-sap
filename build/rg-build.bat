rm -rf .\release
mkdir .\release
rm -f SAPExchange.jar

rem "C:\Program Files\Java\jdk1.8.0_172\bin\javac" -classpath C:\InterSystems\IRIS\dev\java\lib\JDK18\intersystems-gateway-3.0.0.jar;c:\SAP\sapjco3.jar;C:\SAP\sapidoc3.jar -d release .\eclipce-workspace\SAPExchange\SAPOperation\src\ru\intersystems\SAPOperation.java 
"C:\Program Files\Java\jdk1.8.0_172\bin\javac" -classpath C:\InterSystems\IRIS\dev\java\lib\JDK18\intersystems-jdbc-3.0.0.jar;C:\InterSystems\IRIS\dev\java\lib\JDK18\intersystems-gateway-3.0.0.jar;c:\SAP\sapjco3.jar;C:\SAP\sapidoc3.jar -d release ..\src\ru\intersystems\SAPOperation.java 
"C:\Program Files\Java\jdk1.8.0_172\bin\javac" -classpath C:\InterSystems\IRIS\dev\java\lib\JDK18\intersystems-jdbc-3.0.0.jar;C:\InterSystems\IRIS\dev\java\lib\JDK18\intersystems-gateway-3.0.0.jar;c:\SAP\sapjco3.jar;C:\SAP\sapidoc3.jar -d release ..\src\ru\intersystems\SAPService.java 

rem javac -classpath C:\InterSystems\IRIS\dev\java\lib\JDK18\intersystems-gateway-3.0.0.jar -d JavaHostsServiceOut src\JavaHosts\JavaHostsService.java

"C:\Program Files\Java\jdk1.8.0_172\bin\jar" cf .\release\SAPExchange.jar -C release . 
rm -f c:\SAP\SAPExchange.jar
cp -f .\release\SAPExchange.jar c:\SAP\
