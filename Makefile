#
# ----- CONFIG -----
#

.DEFAULT_GOAL := build/test/.make  # build test code by default
.PHONY        := clean check
OSTYPE        := $(shell uname)

# Remember to update the POM file in src/main/maven/pom.xml if this changes
VERSION       := 1.0.0

# -- sources --
JAVA_MAIN_SRC  := $(shell find src/main/java -type f -name "*.java")
JAVA_TEST_SRC  := $(shell find src/test/java -type f -name "*.java")
ANTLR_SRC      := $(shell find src/main/antlr -type f -name "*.g4")

# -- executables --
ANTLR_BIN      := bin/antlr4  # This is created dynamically
ANTLR_VERSION  := 4.9.2

JAVAC_BIN      := $(JAVA_HOME)/bin/javac
JAVADOC_BIN    := $(JAVA_HOME)/bin/javadoc

# -- classpaths --
# The depdendencies for compiling the main codebase
CLASSPATH_MAIN_COMPILE   = build/gen:$(shell CP=""; for lib in `find lib/java/main -type f -name "*.jar"`; do CP="$$CP:$$lib"; done; printf "$$CP" )
# The dependencies for running the code
CLASSPATH_MAIN_RUN       = $(CLASSPATH_MAIN_COMPILE):build/main:src/main/resources:$(shell CP=""; for lib in `find lib/java/runtime -type f -name "*.jar"`; do CP="$$CP:$$lib"; done; printf "$$CP" )
# The dependencies for compiling the test code
CLASSPATH_CHECK_COMPILE  = $(CLASSPATH_MAIN_COMPILE):build/main:build/testgen:$(shell CP=""; for lib in `find lib/java/test -type f -name "*.jar"`; do CP="$$CP:$$lib"; done; printf "$$CP" )
# The dependencies for running the test code
CLASSPATH_CHECK_RUN      = $(CLASSPATH_CHECK_COMPILE):build/test:src/main/resources:src/test/resources:$(shell CP=""; for lib in `find lib/java/runtime_test -type f -name "*.jar"`; do CP="$$CP:$$lib"; done; printf "$$CP" )

#
# ----- CODEGEN -----
#

bin/lib/antlr-$(ANTLR_VERSION).jar:
	# Download the Antlr library
	mkdir -p $(shell dirname "$@")
	curl -L -o bin/lib/antlr-$(ANTLR_VERSION).jar https://www.antlr.org/download/antlr-$(ANTLR_VERSION)-complete.jar

$(ANTLR_BIN): bin/lib/antlr-$(ANTLR_VERSION).jar
	# Create an Antlr executable
	mkdir -p $(shell dirname "$@")
	printf '#!/bin/bash\n' > bin/antlr4
	printf 'java -Xmx500M -cp "$(CURDIR)/bin/lib/antlr-$(ANTLR_VERSION).jar:$$CLASSPATH" org.antlr.v4.Tool $$@\n' >> $(ANTLR_BIN)
	chmod +x $(ANTLR_BIN)


src/gen/antlr/java/.make: $(ANTLR_SRC) \
		| $(ANTLR_BIN) lib/java/.make  # Changes to these files don't force an Antlr recompilation
	$(info Generating Antlr Files...)
	rm -rf src/gen/antlr/java
	mkdir -p $(shell dirname "$@")
	$(ANTLR_BIN) \
		-o src/gen/antlr/java/com/squareup/trex \
		-visitor \
		-Werror \
		-Xexact-output-dir \
		$(ANTLR_SRC)
	$(info Compiling Antlr Files...)
	mkdir -p build/gen
	@$(JAVAC_BIN) \
		-cp "$(CLASSPATH_MAIN_COMPILE)" \
		-d build/gen \
		`find src/gen/antlr/java -type f -name "*.java"`
	touch $@

#
# ----- COMPILE -----
#

lib/java/.make: deps.gradle
ifeq (, $(shell which gradle))
	$(error "Could not find 'gradle' in your path -- this is needed to download dependencies. You can install it with `brew install gradle`")
endif
	mkdir -p lib/java/main lib/java/test
	gradle --no-daemon -b deps.gradle -q copyDependencies
	touch $@

build/main/.make: lib/java/.make $(JAVA_MAIN_SRC) src/gen/antlr/java/.make
	# Compile main codebase
	mkdir -p $(shell dirname "$@")
	$(info Compiling T-Rex...)
	@$(JAVAC_BIN) \
		-cp "$(CLASSPATH_MAIN_COMPILE)" \
		-Xlint:all -Xlint:-path -Xlint:-rawtypes -Xlint:-unchecked -Xlint:-deprecation \
		-d build/main \
		$(JAVA_MAIN_SRC)
	$(info Compilation succeeded)
	touch $@

build/javadoc/.make: lib/java/.make $(JAVA_MAIN_SRC) src/gen/antlr/java/.make
	# Compile main codebase's documentation
	mkdir -p $(shell dirname "$@")
	$(info Documenting T-Rex...)
	@$(JAVADOC_BIN) \
		-cp "$(CLASSPATH_MAIN_COMPILE)" \
		-d build/javadoc \
		$(JAVA_MAIN_SRC) \
		`find src/gen/antlr/java -type f -name "*.java"`
	$(info Documentation build succeeded)
	touch $@

# Cached compilation of the tests
build/test/.make: build/main/.make lib/java/.make $(JAVA_TEST_SRC)
	mkdir -p build/test build/reports
	$(info Compiling Tests...)
	@$(JAVAC_BIN) \
		-Xlint:overrides -Xlint:serial -Xlint:unchecked -Xlint:divzero -Xlint:-deprecation  \
		-cp "$(CLASSPATH_CHECK_COMPILE)" \
		-d build/test \
		$(JAVA_TEST_SRC)
	touch "$@"

# Builds the messenger.jar artifact used in deployments. This can be run from anywhere.
trex-$(VERSION).jar: build/main/.make
	# Create a staging folder for the Jar
	if [[ -d build/jar ]]; then rm -r build/jar; fi
	mkdir -p build/jar
	# Copy this project's compiled classes
	cp -r build/main/* build/jar
	cp -r build/gen/* build/jar
	# Copy resources
	if [ -e src/main/resources ]; then cp -r src/main/resources/* build/jar; fi
	# Jar everything up
	if [[ -e trex.jar ]]; then rm trex.jar; fi
	# Use zip instead of jar because we want to build a deterministic jar and the
	# jar command always overwrites META-INF/MANIFEST.MF with a new timestamp
	# -X : Remove extra metadata that is OS-specific
	# -r : Recursive
	# -y : Preserve symlinks
	cd build/jar && zip -qXry ../../trex.zip . --exclude .make 
	mv trex.zip $@
	# Cleanup staging folder
	rm -r build/jar

trex-$(VERSION)-sources.jar: src/gen/antlr/java/.make
	# Use zip instead of jar because we want to build a deterministic jar and the
	# jar command always overwrites META-INF/MANIFEST.MF with a new timestamp
	# -X : Remove extra metadata that is OS-specific
	# -r : Recursive
	# -y : Preserve symlinks
	# -u : Update the package
	cd src/main/java && zip -qXry ../../../trex-sources.zip . --exclude .make 
	cd src/gen/antlr/java && zip -uqXry ../../../../trex-sources.zip . --exclude .make 
	mv trex-sources.zip $@

trex-$(VERSION)-javadoc.jar: build/javadoc/.make
	# Use zip instead of jar because we want to build a deterministic jar and the
	# jar command always overwrites META-INF/MANIFEST.MF with a new timestamp
	# -X : Remove extra metadata that is OS-specific
	# -r : Recursive
	# -y : Preserve symlinks
	cd build/javadoc && zip -qXry ../../trex-javadoc.zip . --exclude .make 
	mv trex-javadoc.zip $@

trex-$(VERSION).pom: src/main/maven/trex-$(VERSION).pom
	cp "$<" "$@"

#
# ----- DEPLOYMENT -----
#

# Package the Java code for Maven Central.
# You must have gpg installed, and have a key that you can sign with. This should
# likely be Gabor's key (gabor@squareup.com) for actual deployment,
#
# This bundle can be deployed using the steps outlined here:
# https://central.sonatype.org/publish/publish-manual/#bundle-creation
trex-$(VERSION)-bundle.jar: trex-$(VERSION).jar trex-$(VERSION)-sources.jar trex-$(VERSION)-javadoc.jar trex-$(VERSION).pom
	# Sign all the files
	for file in $^; do \
  	gpg --batch --yes --detach-sign --armor $$file; \
	done
	# Use zip instead of jar because we want to build a deterministic jar and the
	# jar command always overwrites META-INF/MANIFEST.MF with a new timestamp
	# -X : Remove extra metadata that is OS-specific
	# -r : Recursive
	# -y : Preserve symlinks
	zip -qX "$@" $^ $(subst .pom,.pom.asc,$(subst .jar,.jar.asc,$^))
	# Clean up
	rm $(subst .pom,.pom.asc,$(subst .jar,.jar.asc,$^))
	rm trex-$(VERSION).pom


#
# ----- CHECK -----
#

# This block allows overwriting of the unit tests that are run via the
# enviornmental variable TEST_ARGS. If it is not set, then all tests are run.
#
# The set of valid values for TEST_ARGS is documented here:
#  https://junit.org/junit5/docs/current/user-guide/#running-tests-console-launcher-options
#
# For example, one can run all the tests in a particular class via the
# following command:
#
#   TEST_ARGS='--select-class=com.squareup.trex.PatternTest' make check
#
TEST_ARGS ?= --scan-classpath

check: build/test/.make
	$(info Running tests...)
	@java \
		-mx500m \
		-ea \
		-cp "$(CLASSPATH_CHECK_RUN)" \
		-Dlog.level=warn \
		org.junit.platform.console.ConsoleLauncher \
		--fail-if-no-tests \
		--disable-banner \
		--details=summary \
		--reports-dir=build/reports \
		--include-classname=com.squareup.trex.* \
		$(TEST_ARGS)

#
# ----- CLEAN -----
#

clean:
	if [[ -e trex-$(VERSION)-bundle.jar ]]; then \
		rm trex-$(VERSION).jar trex-$(VERSION)-sources.jar trex-$(VERSION)-javadoc.jar trex-$(VERSION).pom; \
		rm trex-$(VERSION).jar.asc trex-$(VERSION)-sources.jar.asc trex-$(VERSION)-javadoc.jar.asc trex-$(VERSION).pom.asc; \
		rm trex-$(VERSION)-bundle.jar; \
	fi
	if [[ -d build/ ]]; then rm -r build; fi
	if [[ -d src/gen ]]; then rm -r src/gen; fi

distclean: clean
	if [[ -d lib/ ]]; then rm -r lib; fi
	if [[ -e bin/lib/antlr-$(ANTLR_VERSION).jar ]]; then rm bin/lib/antlr-$(ANTLR_VERSION).jar; fi
	if [[ -e $(ANTLR_BIN) ]]; then rm $(ANTLR_BIN); fi
	if [[ -d bin/lib ]]; then rmdir bin/lib; fi
	if [[ -d bin ]]; then rmdir bin; fi
	if [[ -e .gradle ]]; then rm -r .gradle; fi
