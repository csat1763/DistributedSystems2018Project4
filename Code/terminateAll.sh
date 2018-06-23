#!/bin/bash

main()
{


	mkdir -p "all-instances"

	if [ -e jsons/regions.json ]
	then
		sleep .0001
	else
		echo "[*] Loading regions ..."
		aws ec2 describe-regions > jsons/regions.json
	fi


	size=$(cat jsons/regions.json | jq '.Regions | length' )
	for (( i=0; i<$size; i++ ))
	do
		regionName=$(cat jsons/regions.json | jq -r .Regions[$i].RegionName )
		aws --region=$regionName ec2 describe-instances > all-instances/"instances-$regionName.json" &
		aws --region=$regionName ec2 describe-spot-instance-requests > all-instances/"request-$regionName.json"
	done

	waitJobs

	for (( i=0; i<$size; i++ ))
	do
		regionName=$(cat jsons/regions.json | jq -r .Regions[$i].RegionName )
		removeFromRegion "all-instances/instances-$regionName.json" "$regionName" &
		cancelSpotReq "all-instances/request-$regionName.json" "$regionName" &
	done

	waitJobs

	rm -rf "all-instances"
}

waitJobs()
{
	for job in `jobs -p`
	do
		wait $job
	done
}

removeFromRegion()
{
	instaceFile=$1
	region=$2

	resSize=$(cat $instaceFile | jq '. | length')
	for (( i=0; i<$resSize; i++ ))
	do
		instanceAmount=$(cat $instaceFile | jq --arg i $i '.Reservations | length')
		for (( j=0; j<$instanceAmount; j++ ))
		do
			instanceAmount2=$(cat $instaceFile | jq --arg i $i --arg j $j '.Reservations[$j | tonumber].Instances | length')
			for (( k=0; k<$instanceAmount2; k++ ))
			do
				status=$(cat $instaceFile | jq -r .Reservations[$j].Instances[$k].State.Name)
				instanceID=$(cat $instaceFile | jq -r .Reservations[$j].Instances[$k].InstanceId )
				if [ $status = "running" ];
				then 
					aws --region=$region ec2 terminate-instances --instance-ids $instanceID &
					echo Shutting down: $instanceID @ $region
				else
					echo Already stopped: $instanceID @ $region
				fi
			done
		done
	done
}

cancelSpotReq()
{
	spotFile=$1
	region=$2

	reqSize=$(cat $spotFile | jq '.SpotInstanceRequests | length')

	for (( i=0; i<$reqSize; i++ ))
	do
		reqId=$(cat $spotFile | jq -r --arg i $i .SpotInstanceRequests[$i].SpotInstanceRequestId)
		aws --region=$region ec2 cancel-spot-instance-requests --spot-instance-request-ids $reqId &
		echo Canelling request $reqId ...
	done



}

if [[ "$OSTYPE" == "linux-gnu" ]]; then
	#echo mounting jq for LINUX
	if [[ "$uname -m" == "x86_64" ]]; then
		jq(){
			./jq-linux64 "$@"
		}
	else
		jq(){
			./jq-linux32 "$@"
		}
	fi
elif [[ "$OSTYPE" == "cygwin" ]]; then
	jq(){
		./jq-win64.exe "$@"
	}

elif [[ "$OSTYPE" == "msys" ]]; then
	jq(){
		./jq-win64.exe "$@"
	}

	#echo mounting jq for MS
fi

awsAccesKeyID=$(cat credentials.json | jq -r .awsAccesKeyID)
awsSecretAccessKey=$(cat credentials.json | jq -r .awsSecretAccessKey)

aws configure set aws_access_key_id $awsAccesKeyID
aws configure set aws_secret_access_key $awsSecretAccessKey

main






