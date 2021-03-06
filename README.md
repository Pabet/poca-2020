# POCA 2020

Our product is a marketplace connecting buyers to sellers. Similar products are: Amazon.com, Rakuten, Cdiscount.com, Veepee...

Online shopping is not original at all but it has a rich domain with interesting choices to make. Let's view it as a playground where we can either borrow ideas from our competitors or build our own vision of what a marketplace should be!

## Install instructions

To run the software locally, Docker and postgresql is needed.

In addition, scala, sbt and terraform are needed for development.

### Create the database

To connect: `sudo -u postgres psql`
```
postgres=# create database poca;
CREATE DATABASE
postgres=# create user poca with encrypted password 'poca';
CREATE ROLE
postgres=# grant all privileges on database poca to poca;
GRANT
postgres=# \connect poca
You are now connected to database "poca" as user "postgres".
poca=# alter schema public owner to poca;
ALTER SCHEMA
```

In `pg_hba.conf`, make sure there is a way to connect as poca:
* `local poca poca md5` to connect using `psql`
* `host poca poca 127.0.0.1/32 md5` to connect using TCP.

Restart the database. Test the connection with `psql poca poca`.

If you plan to run tests, you need to create another database `pocatest`.
```
postgres=# create database pocatest;
CREATE DATABASE
postgres=# create user pocatest with encrypted password 'pocatest';
CREATE ROLE
postgres=# grant all privileges on database pocatest to pocatest;
GRANT
postgres=# \connect pocatest
You are now connected to database "pocatest" as user "postgres".
poca=# alter schema public owner to pocatest;
ALTER SCHEMA
```

### Create the tables

```
sbt "runMain poca.AppHttpServer"
```

## Run the tests

```
sbt clean coverage test coverageReport
```

This also creates a coverage report at [target/scala-2.13/scoverage-report/index.html](target/scala-2.13/scoverage-report/index.html).


## Run the software

### Use the software online

Go to http://15.188.106.94/hello

### Run locally using the Docker image from Docker Hub

```
docker run poca/poca-2020:latest
```

#### Use docker without sudo
If you get the following error :

```docker: Got permission denied while trying to connect to the Docker daemon socket at unix:///var/run/docker.sock: Post http://%2Fvar%2Frun%2Fdocker.sock/v1.35/containers/create: dial unix /var/run/docker.sock: connect: permission denied.See 'docker run --help'.```


consider configure docker to be used without sudo :
```
sudo groupadd docker
sudo usermod -aG docker $USER
newgrp docker
```
If you still get the error reboot your machine.

### Run from the local directory

```
sbt run
```

Then visit `http://localhost:8080/hello`

## Package to a Docker image

```
sbt docker:publishLocal
```

Then the image with name `poca-2020` and tag `latest` is listed. (There is also an image `poca-2020:0.1.0-SNAPSHOT` that is identical).

```
docker image ls
```

Run the docker image locally:

```
docker run --net=host poca-2020:latest
```

To remove old images:

```
docker image prune
```

## Deployment

In the directory `terraform`, to initialize the project:

```
terraform init -backend-config="access_key=<ACCESS_KEY>" -backend-config="secret_key=<SECRET_KEY>"
```

Set the secrets in you shell:

```
export TF_VAR_db_password="xxx"
```

To plan the deployment:

```
terraform plan --var-file=integration.tfvars
```

To deploy:

```
terraform apply --var-file=integration.tfvars
```

To destroy:

```
terraform destroy --var-file=integration.tfvars
```

## Logs

Logs are stored on AWS Cloudwatch: https://eu-west-3.console.aws.amazon.com/cloudwatch/home?region=eu-west-3#logsV2:log-groups/log-group/poca-web/log-events
