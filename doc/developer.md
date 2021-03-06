# Development

> This document contains instructions for building and running RSA for
> development. If you are setting up a production environment, see
> [`production.md`](production.md).

Before building, it's a good idea to start
[`docker-proxy` with SSL support][dp]: this allows caching of libraries
used by Gradle. The rsa's build script has special support for `docker-proxy`
to allow caching of SSL requests.

Use [docker-compose][dc] to build the RSA.

```
sudo docker-compose build
```

The following services are provided:

 - `postgres`: A metadata store instance for the RSA.
 - `data`: A data-only container with volumes for tile storage.
 - `dev`: A development environment that drops you into a shell in the source
    directory.
 - `web`: The web services running in Tomcat.
 - `worker`: Backend cluster nodes.
 - `seed`: Cluster manager.

Copy the config to an out-of-source location so you can modify it without git
noticing. If you follow this convention, docker-compose will mount it as a
volume when starting your containers:

```
mkdir -p ../config
cp -a config ../config/rsa
```

Note that during testing, the config in `test-config` will be used instead.
Changes to test-config should usually be checked in to the source code, so
there's no need to copy it to an out-of-source location.

Start a new local RSA cluster with:

```
sudo docker-compose up -d web seed worker
sudo docker-compose scale worker=5
```

Start as many workers as you like, but make sure you have at least two: the
first one doesn't do any actual work. All of the cluster nodes will write
data in the `postgres` and `data` containers. They have volumes mapped to the
live source directory, so any changes you make to your code will show up in the
running instances (perhaps after a compile and restart).

To develop new code, start the dev environment:

```
sudo docker-compose run --rm dev
```

Then you can build with [Gradle]. For example, to start a [continuous build][cb]
of the query engine:

```
cd rsaquery
gradle --daemon --continuous compileJava
```

After the first dev build, you will have `.classpath.txt` files in your source
directory. These can be used for autocompletion, e.g. using [Atom's
autocomplete-java package][aj].


## Redeploying

The source directories are mounted as Docker volumes, so changes you make should
immediately show up in the running containers. However you may need to restart
them for the changes to be visible:

```
sudo docker-compose up -d --force-recreate --no-deps web
```

You can rebuild and relaunch individual services like this:

```
sudo docker-compose up -d --build --no-deps web worker
```

[Docker]: https://docs.docker.com/linux/
[dc]: https://docs.docker.com/compose/install/


## Stopping

To stop it run:

```
sudo docker-compose stop
```

**Warning:** `docker-compose down` will remove your databases, and you'll have
to import your data again.

[dp]: https://github.com/silarsis/docker-proxy
[dc]: https://docs.docker.com/compose/
[Gradle]: https://gradle.org/
[cb]: https://docs.gradle.org/current/userguide/continuous_build.html
[aj]: https://atom.io/packages/autocomplete-java
