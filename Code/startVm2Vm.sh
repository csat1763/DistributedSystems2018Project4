#!/bin/bash


#fallocate -l 200M 1.dat;
#fallocate -l 300M 2.dat;
#fallocate -l 400M 3.dat;
#fallocate -l 500M 4.dat;
#fallocate -l 1G 5.dat;
#fallocate -l 2G 6.dat;

main()
{
	dnsName=$1
	vm2name=$2
	keyName=$3
	keypath=$4
	keypath2=$keypath
	storepath=$6
	zone=$5
	archtiec=$7
	scriptName="stopTime.sh"


	awsAccesKeyID=$(cat credentials.json | jq -r .awsAccesKeyID)
	awsSecretAccessKey=$(cat credentials.json | jq -r .awsSecretAccessKey)

	script="
	fallocate -l 10M 0.dat;
	fallocate -l 200M 1.dat;
	fallocate -l 300M 2.dat;
	fallocate -l 400M 3.dat;
	fallocate -l 500M 4.dat;
	fallocate -l 1G 5.dat;
	fallocate -l 2G 6.dat;


	aws configure set aws_access_key_id $awsAccesKeyID;
	aws configure set aws_secret_access_key $awsSecretAccessKey;
	chmod 400 $keyName.pem;
	chmod +x $scriptName;
	./$scriptName $vm2name $keyName $zone $archtiec;
	echo VM-1 done;
	"
	
	fastScript="
	fallocate -l 10M 0.dat;
	fallocate -l 10M 1.dat;
	fallocate -l 10M 2.dat;
	fallocate -l 10M 3.dat;
	fallocate -l 10M 4.dat;
	fallocate -l 10M 5.dat;
	fallocate -l 10M 6.dat;


	aws configure set aws_access_key_id $awsAccesKeyID;
	aws configure set aws_secret_access_key $awsSecretAccessKey;
	chmod 400 $keyName.pem;
	chmod +x $scriptName;
	./$scriptName $vm2name $keyName $zone $archtiec;
	echo VM-1 done;
	"


	while true; do
		scp -i "$keypath$keyName.pem" -o StrictHostKeyChecking=no "$scriptName" ec2-user@$dnsName:~
		#exitstatus 1 -> something went wrong -> keep trying
		if [ "$?" = "1" ];
		then
			echo "[*] Filecopy failed! Trying again..."
			sleep 2
		else
			break
		fi
	done
	scp -i "$keypath$keyName.pem" -o StrictHostKeyChecking=no "$keypath2$keyName.pem" ec2-user@$dnsName:~


	#the script to be executed with the ssh-req
	echo "[*] Connecting via SSH..."
	#try connecting via ssh until success
	while true; do
		#connect via ssh with script-exec instruction once connected
		ssh -i "$keypath$keyName.pem" -o StrictHostKeyChecking=no ec2-user@$dnsName $fastScript
		if [ "$?" = "255" ];
		then
			echo "Connection refused! Trying again..."
			sleep 2
		else
			break
		fi
	done



	region1=$(cat $storepath/spot-pair.json | jq -r .[0].Zone)
	if [ $region1 = $zone ]
	then
		indx=0
	else
		indx=1
	fi
	storepath2="$storepath/@results"
	scp -i "$keypath$keyName.pem" -o StrictHostKeyChecking=no ec2-user@$dnsName:$zone-VM1toVM2.json "$(pwd)/$storepath2/"

	echo -e "\"instanceType\":\"$(cat $storepath/spot-pair.json | jq -r .[0].InstanceType)\"," >> "$storepath2/$zone-VM1toVM2.json"
	echo -e "\"zone\":\"$zone\"," >> "$storepath2/$zone-VM1toVM2.json"
	echo -e "\"price\":\"$(cat $storepath/spot-pair.json | jq -r .[$indx].SpotPrice)\"," >> "$storepath2/$zone-VM1toVM2.json"
	echo -e "\"time\":\"ms\",\n\"fileSize\":\"byte\"\n}" >> "$storepath2/$zone-VM1toVM2.json"



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

main $@





