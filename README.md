[![Build Status](https://github.com/PANTHEONtech/triemap/actions/workflows/maven.yml/badge.svg?event=push)](https://github.com/PANTHEONtech/triemap/actions/workflows/maven.yml)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=PANTHEONtech_triemap&metric=coverage)](https://sonarcloud.io/summary/new_code?id=PANTHEONtech_triemap)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=PANTHEONtech_triemap&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=PANTHEONtech_triemap)
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/2172/badge)](https://bestpractices.coreinfrastructure.org/projects/2172)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2FPantheonTechnologies%2Ftriemap.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2FPantheonTechnologies%2Ftriemap?ref=badge_shield)
[![CodeQL](https://github.com/PANTHEONtech/triemap/actions/workflows/codeql.yml/badge.svg?event=push)](https://github.com/PANTHEONtech/triemap/actions/workflows/codeql.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/tech.pantheon.triemap/triemap/badge.svg)](https://maven-badges.herokuapp.com/maven-central/tech.pantheon.triemap/triemap)
[![Javadocs](https://www.javadoc.io/badge/tech.pantheon.triemap/triemap.svg)](https://www.javadoc.io/doc/tech.pantheon.triemap/triemap)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## About
This is a Java port of a concurrent trie hash map implementation from the Scala collections library. It used to be an almost line-by-line 
conversion from Scala to Java. These days it has been refactored to be Java 8 friendly and make some original assertions impossible via
refactoring.

Idea + implementation techniques can be found in these reports written by Aleksandar Prokopec:
   * http://infoscience.epfl.ch/record/166908/files/ctries-techreport.pdf - this is a nice introduction to Ctries, along with a correctness proof
   * http://lamp.epfl.ch/~prokopec/ctries-snapshot.pdf - a more up-to-date writeup which describes the snapshot operation

The code origins can be tracked through these links:
   *   [Original Java port](https://github.com/romix/java-concurrent-hash-trie-map)
   *   [Scala implementation](https://github.com/scala/scala/blob/930c85d6c96507d798d1847ea078eebf93dc0acb/src/library/scala/collection/concurrent/TrieMap.scala)

Some of the tests and implementation details were borrowed from this project:
   *  https://github.com/flegall/concurrent-hash-trie

Implementation status : 
   *   The given implementation is complete and implements all features of the original Scala implementation including support for 
       snapshots.
   *   Wherever necessary, code was adapted to be more easily usable in Java, e.g. it returns Objects instead of Option<V> as 
       many methods of Scala's collections do.
   *   This class implements all the ConcurrentMap & Iterator methods and passes all the tests. Can be used as a drop-in replacement
       for usual Java maps, including ConcurrentHashMap.
   *   The code take advantage of Java 8 to supplant Scala constructs
   *   The implementation is a Java 9+ JPMS module and can easily be depended upon by other modules


## What is a concurrent trie hash map also known as ctrie?
ctrie is a lock-Free Concurrent Hash Array Mapped Trie.

A concurrent hash-trie or Ctrie is a concurrent thread-safe lock-free implementation of a hash array mapped trie.
 
It is used to implement the concurrent map abstraction. It has particularly scalable concurrent insert and remove operations 
and is memory-efficient. 

It supports O(1), atomic, lock-free snapshots which are used to implement linearizable lock-free size, iterator and clear operations. 
The cost of evaluating the (lazy) snapshot is distributed across subsequent updates, thus making snapshot evaluation horizontally scalable.

The original Scala-based implementation of the Ctrie is a part of the Scala standard library since the version 2.10.

More info about Ctries:

- http://infoscience.epfl.ch/record/166908/files/ctries-techreport.pdf - this is a nice introduction to Ctries, along with a correctness proof
- http://lamp.epfl.ch/~prokopec/ctries-snapshot.pdf - a more up-to-date writeup (more coherent with the current version of the code) which describes the snapshot operation

## Required Java versions
There are multiple release trains of this library:
   * Versions 1.1.x require Java 8 or later
   * Versions 1.2.x require Java 11 or later
   * Versions 1.3.x require Java 17 or later

## License
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2FPantheonTechnologies%2Ftriemap.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2FPantheonTechnologies%2Ftriemap?ref=badge_large)


## Usage
Usage of this library is very simple. Simply import the class tech.pantheon.triemap.TrieMap and use it as a usual Map.

```java
    import tech.pantheon.triemap.TrieMap;

    Map<String, String> myMap = TrieMap.create();
    myMap.put("key", "value");
```

## Building the library

Use a usual `mvn clean install`

## Using the library with Maven projects
The prebuilt binaries of the library are available from Maven central. Please use the following dependency in your POM files:

```xml
    <dependency>
        <groupId>tech.pantheon.triemap</groupId>
        <artifactId>triemap</artifactId>
        <version>1.3.0</version>
    </dependency>
```

## External dependencies
This library is self-contained. It does not depend on any additional libraries. In particular, it does not require the rather big Scala's 
standard library to be used.


## Contributing
All contributions are welcome! The mechanics follows GitHub norms: we use GH issues to track bugs and improvements. In terms of coding style,
the project follows OpenDaylight's code style -- which is a combination of Google's style guidelines and a few tweaks here and there -- these
are enforced by CheckStyle. Each code contribution should have an attached unit test, for bug fixes this is a strict requirement.
We are also using SpotBugs for static code analysis and prefer no @SuppressFBWarnings. If a suppression is needed, its scope must be minimal
and it must carry a justification.
