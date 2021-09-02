Changelog
====

### 2.0

Release date: Sep 02, 2021

Breaking changes:

* [PR #26](https://github.com/jenkinsci/extras-executable-war/pull/26) -
  Stop supporting Java 1.6 and 1.7 in the executable.
  Going forward, there will be no graceful error reporting for Java versions below Java 8.
  Jenkins will not be affected, bercause Java 1.7 has not been supported for more than 3 years.
* [PR #27](https://github.com/jenkinsci/extras-executable-war/pull/27) - 
  Remove the `--daemon` option that is no longer used in Jenkins.
  The system `daemon` and `daemonize` should be used as a replacement.

Other changes:

* [JENKINS-58058](https://issues.jenkins-ci.org/browse/JENKINS-58058) - Fix minor issues reported by SpotBugs
* [PR #23](https://github.com/jenkinsci/extras-executable-war/pull/23) -
  Move the project codebase to Java 8

### 1.45

Release date: Feb 07, 2019

* [JENKINS-52285](https://issues.jenkins-ci.org/browse/JENKINS-52285) -
Allow running with Java 11 without setting 
the `--enable-future-java` flag or the environment variable
  * It is still possible to run with Java 9 and 10 using this flag,
    but these versions are [not supported](https://jenkins.io/doc/administration/requirements/java/) by the Jenkins project 

### 1.44

Release date: Jan 16, 2019

* [PR-20](https://github.com/jenkinsci/extras-executable-war/pull/20) - 
Allow using `JENKINS_ENABLE_FUTURE_JAVA` environment variable to do the same as the CLI switch `--enable-future-java`

### 1.42 & 1.43

Burnt versions.

### 1.41

Release date: Jun 22, 2018

* [JENKINS-51994](https://issues.jenkins-ci.org/browse/JENKINS-51994) -
Warnings for unsupported Java versions now include the link to
the [Java Support Page](https://jenkins.io/redirect/java-support)
* [JENKINS-46622](https://issues.jenkins-ci.org/browse/JENKINS-46622) -
Fix the Illegal Reflective Access warning on startup when running
with Java 9+ (experimental support)

### 1.40

Release date: Jun 10, 2018

* [JENKINS-51155](https://issues.jenkins-ci.org/browse/JENKINS-51155) -
Add a `--enable-future-java` flag, which allows running with not-yet-supported Java versions

### 1.39

Release date: Mar 28, 2018

* [JENKINS-50439](https://issues.jenkins-ci.org/browse/JENKINS-50439) -
  Remove spurious "Failed to delete the temporary Winstone file /tmp/winstone/jenkins-2.x.war" warning when starting Jenkins

### 1.38

Release date: Mar 10, 2018

* [JENKINS-49737](https://issues.jenkins-ci.org/browse/JENKINS-49737) -
Print warning if the executable is started with Java 9 or above.
* [PR #15](https://github.com/jenkinsci/extras-executable-war/pull/15) -
Developer: Update to the newest parent POM, cleanup issues reported by static analysis.

### 1.37

Release date: Jan 08, 2018

* [PR #13](https://github.com/jenkinsci/extras-executable-war/pull/13) -
Add ability to supply WAR command-line arguments via STDIN using the `--paramsFromStdIn` parameter
([Documentation](https://github.com/jenkinsci/extras-executable-war#parameters-from-stdin)).
* [JENKINS-22088](https://issues.jenkins-ci.org/browse/JENKINS-22088) -
Prevent the disk space leak due to multiple copies of `winstone-XXXX.jar` in the TEMP folder.

### 1.36

Release date: July 14, 2017

* [JENKINS-45438](https://issues.jenkins-ci.org/browse/JENKINS-45438) -
Set context classloader to enable the ServiceLoader mechanism required for HTTP/2 support
([PR #11](https://github.com/jenkinsci/extras-executable-war/pull/11)).

### 1.35.1

Release date: June 16, 2017

Fixed issues:

* [JENKINS-44894](https://issues.jenkins-ci.org/browse/JENKINS-44894) -
Fix typo in the system property name
([PR #10](https://github.com/jenkinsci/extras-executable-war/pull/10)).

### 1.35

Release date: June 14, 2017

* [JENKINS-44764](https://issues.jenkins-ci.org/browse/JENKINS-44764) -
Executable WAR now checks for Java 8 instead of Java 7 before starting Jenkins
([PR #8](https://github.com/jenkinsci/extras-executable-war/pull/8)).
* [JENKINS-44894](https://issues.jenkins-ci.org/browse/JENKINS-44894) -
Add system properties for managing the Jetty Session ID cookie name
([PR #9](https://github.com/jenkinsci/extras-executable-war/pull/9)).
  * Now it is possible to disable random cookie name generation or to specify a custom one
  * These options can be used to workaround issues like [JENKINS-25046](https://issues.jenkins-ci.org/browse/JENKINS-25046)
  * The options are documented [here](README.md#jetty-session-ids)

### Previous releases

See the commit history and [Jenkins changelog](http://jenkins-ci.org/changelog).
