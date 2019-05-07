Project website: [`https://ncik-roberts.github.io/15745/`](https://ncik-roberts.github.io/15745/)

#### Dependencies
  * Java 12
    * *MacOS.* Download the MacOS tar.gz archive from the [OpenJDK website](https://jdk.java.net/12/). Untar the archive, and move the contained directory (named something like `jdk-12.0.1.jdk`) to the `/Library/Java/JavaVirtualMachines/` directory.
    * *Linux.* Download the Linux tar.gz archive from the [OpenJDK website](https://jdk.java.net/12/), untar the archive, and add both the untarred archive and its `bin` directory to your path. For example, if the untarred contents of the archive are placed at `/usr/lib/jdk-12.0.1`, you would append the paths `/usr/lib/jdk-12.0.1` and `/usr/lib/jdk-12.0.1/bin` to your `PATH` environment variable.
    * *Windows.* To install OpenJDK, download the Windows zip file from the [OpenJDK website](https://jdk.java.net/12/), and follow the instructions at [this StackOverflow post](https://stackoverflow.com/questions/52511778/how-to-install-openjdk-11-on-windows/52531093#52531093) to correctly set Windows environment variables. Alternatively, if you wish to just use an install wizard, [download and install the Oracle JDK](https://www.oracle.com/technetwork/java/javase/downloads/jdk12-downloads-5295953.html).
  * Gradle >=5.0
    * `brew install gradle` should work on MacOS.
    * `sdk install gradle 5.4.1` should work on Linux.
    * [This website](https://gradle.org/install/) will walk you through, otherwise.

#### How to run

To run the pointer analysis on the series of benchmarks (about 11 minutes):

```
$ gradle run
```

To run the pointer analysis, verifying correctness:

```
$ IPA_DEBUG=1 gradle run
```

To run the unit tests:

```
$ gradle test
```

The properties of correctness that `IPA_DEBUG` checks are the following:
  * At >=1, that removing and then re-adding a statement from the program acts as the identity function on the pointer analysis graph. (I.e., adding an edge reverses the changes induced by removing that edge.)
  * At >=2, that for each node in the pointer analysis graph, its points-to set is exactly the union of its predecessors (except for any allocation site, whose points-to sets is exactly itself).
  * At >=3, also prints verbose debugging information.
