MVNCMD := mvn -B ${MVNARGS}
POSTRUN := rm -rf .testcontainers*
CLEANUP := rc=$$?; $(POSTRUN); exit $$rc

TAG := latest

# handle release args
ifeq (release,$(firstword $(MAKECMDGOALS)))
  # use the rest as arguments for "release"
  RELEASE_ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
  # ...and turn them into do-nothing targets
  $(eval $(RELEASE_ARGS):;@:)
endif

all: compile
	$(MVNCMD); $(CLEANUP)

compile: $(wildcard src/main/java/**/*.java)
	$(MVNCMD) compile; $(CLEANUP)

test: $(wildcard src/test/java/**/*.java)
	$(MVNCMD) test; $(CLEANUP)

.PHONY: release image

clean:
	$(MVNCMD) clean; $(CLEANUP)

release:
	$(eval SHELL := /bin/bash)
	git remote update
	if git show-branch *master | grep -A 1 '^--$$' | grep -q remotes; then\
		echo Your local repository is not up to date; exit 1;\
	fi
	$(eval CURRENT_VERSION := $(shell xmllint --xpath '/*[local-name()="project"]/*[local-name()="version"]/text()' pom.xml))
	$(eval VERSION := $(if $(RELEASE_ARGS),$(RELEASE_ARGS),$(subst -SNAPSHOT,,$(CURRENT_VERSION))))
	git flow release start "$(VERSION)"
	perl -pi -e 's,<version>$(CURRENT_VERSION)</version>,<version>'"$(VERSION)"'</version>,' pom.xml
	git commit pom.xml -m "bump release to $(VERSION)"
	git flow release finish -m "release $(VERSION)" </dev/null
	perl -pi -e 'BEGIN{sub bump{@v=split(/\./,$$_[0]);join(".",@v[0..1]).".".($$v[-1]+1);}}s,<version>($(VERSION))</version>,"<version>".(bump($$1))."-SNAPSHOT</version>",e' pom.xml
	git commit pom.xml -m "develop back to snapshot mode"

push:
	git push --all && git push --tags
