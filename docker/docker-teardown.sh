docker stop kafka
docker stop zookeeper
docker stop ethsync
docker stop schema-registry
docker stop ksql-server 
docker stop ethsync_setOffset
docker stop ksql-cli 

docker rm kafka
docker rm zookeeper
docker rm ethsync
docker rm schema-registry
docker rm ksql-server 
docker rm ethsync_setOffset
docker rm ksql-cli 

