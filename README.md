# Executable WAR header
This module defines the 'main' method for a self-contained web application that kickstarts
embedded [Winstone](http://github.com/jenkinsci/winstone) and runs a web application without
needing a servlet container.


See Jenkins war module for how this module gets used by a .war file.

## Usage

This section describes particular use-cases of the executable WAR.

### Jetty Session IDs

The Executable WAR library has a custom handling of Jetty Session IDs by default.
In particular, by default it uses custom cookie names in order to prevent session collisions 
when running multiple Jenkins instances on the same host.
Every startup the wrapper picks a new random cookie name (format: `JSESSIONID.${* random hex symbols}`).
The default lifetime of these cookies is 1 day, hence in some cases you may get into issues like [JENKINS-25046 (too many active cookies)](https://issues.jenkins-ci.org/browse/JENKINS-25046) when you restart Jenkins too often or use multiple instances.

Starting from version `TODO`, it is possible to customize the behavior via System Properties:

* `executableWar.jetty.disableCustomSessionIdCookieName` - 
(`boolean`, default: `false`) -  
Disables usage of the custom cookie names when starting the WAR file.
If the flag is specified, the session ID will be defined by the internal Jetty logic.
In such case it becomes configurable via [Jetty configuration](http://www.eclipse.org/jetty/documentation/9.4.x/quick-start-configure.html) (XML config file, etc.).
* `executableWar.jetty.sessionIdCookieName` - 
(`string`, default: `null`) -  
Sets a custom Session ID Cookie name when `disableCustomSessionIdCookieName` is `false`.
In such case the Jenkins administrator is responsible for preventing cookie collisions between Jenkins instances.


