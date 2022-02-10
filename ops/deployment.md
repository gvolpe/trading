# Deployment on local cluster

These are the quick instructions to deploy the system locally in a k8s cluster via `minikube`.

## Publish docker images to local registry

First, create the docker images from the root folder of this project.

```console
$ eval $(minikube docker-env)
$ docker build -t jdk17-curl modules/
$ sbt docker:publishLocal
```

## Start local cluster

Next, start the local cluster using `minikube`.

```console
$ minikube start
😄  minikube v1.24.0 on Nixos 22.05 (Quokka)
✨  Using the docker driver based on existing profile
👍  Starting control plane node minikube in cluster minikube
🚜  Pulling base image ...
🔄  Restarting existing docker container for "minikube" ...
🐳  Preparing Kubernetes v1.22.3 on Docker 20.10.8 ...
🔎  Verifying Kubernetes components...
    ▪ Using image gcr.io/k8s-minikube/storage-provisioner:v5
🌟  Enabled addons: storage-provisioner, default-storageclass
🏄  Done! kubectl is now configured to use "minikube" cluster and "default" namespace by default
```

You can always stop the local cluster as follows.

```console
$ minikube stop
✋  Stopping node "minikube"  ...
🛑  Powering off "minikube" via SSH ...
🛑  1 node stopped.
```

## Deploy pods

First we deploy Pulsar and Redis.

```console
$ kubectl apply -f ops/infra
deployment.apps/pulsar created
deployment.apps/redis created
service/redis configured
```

After a minute or so we can deploy the application services.

```console
$ kubectl apply -f ops/apps
deployment.apps/alerts created
service/alerts configured
networkpolicy.networking.k8s.io/app configured
deployment.apps/forecasts created
service/forecasts configured
deployment.apps/processor created
service/processor configured
deployment.apps/snapshots created
service/snapshots configured
deployment.apps/ws-server created
service/ws-server configured
```

Check the status of the pods.

```console
$ kubectl get pods
NAME                         READY   STATUS    RESTARTS   AGE
alerts-78846666d-cjqxf       1/1     Running   0          41s
forecasts-6dd77757bb-ntwsx   1/1     Running   0          41s
processor-6f89879764-tvb2h   1/1     Running   0          41s
pulsar-75f65c75fc-fthpp      1/1     Running   0          2m1s
redis-cd5c5d4d7-cpjlg        1/1     Running   0          2m1s
snapshots-85856767b4-fsddk   1/1     Running   0          41s
ws-server-668c9dccb8-kvtvv   1/1     Running   0          41s
```

To stop all pods, run this.

```console
$ kubectl delete deployments alerts forecasts processor pulsar redis snapshots ws-server
deployment.apps "alerts" deleted
deployment.apps "forecasts" deleted
deployment.apps "processor" deleted
deployment.apps "pulsar" deleted
deployment.apps "redis" deleted
deployment.apps "snapshots" deleted
deployment.apps "ws-server" deleted

$ kubectl get pods
No resources found in default namespace.
```

See the [kubectl cheatsheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/) to learn more.
