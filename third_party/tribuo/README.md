# Tribuo training dependency

Tribuo 4.3.2, Oracle and its contributors.
Source: https://github.com/oracle/tribuo
Documentation: https://tribuo.org/learn/4.3/
License: Apache License 2.0 (see LICENSE).
Maven dependency: `org.tribuo:tribuo-classification-tree:4.3.2`.

Used only by the desktop Java training module. Tribuo and its transitive library
binaries are resolved through Gradle and are not included in the APK or source ZIP.
The exported forest is interpreted by the project's small Java predictor.
