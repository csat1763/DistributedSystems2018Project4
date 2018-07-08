#!/bin/bash

main(){

	architec=$1
	spotYes=$2
	whichZone=$3

	tempFolderName="$(date +%s%N| sha1sum | tr -cd '[[:alnum:]]')"

	if [ -e spots/$architec/spot-pair.json ]
	then
		sleep .0001
	else
		echo "[*] Loading folders and files ..."
		create_data
	fi

	zone1=$(cat "spots/$architec/spot-pair.json" | jq -r .[0].Zone)
	zone2=$(cat "spots/$architec/spot-pair.json" | jq -r .[1].Zone)

	#Run measurements either for best zone, 2nd best zone or both concurrently
	if [[ $whichZone = "1" ]];
	then
		mkdir -p spots/$architec/$zone1/$tempFolderName
		start_instance_pair $architec $zone1 $spotYes $tempFolderName
		remove_temp_files "spots/$architec/$zone1/$tempFolderName" "$zone1" 

	elif [[ $whichZone = "2" ]];
	then
		mkdir -p spots/$architec/$zone2/$tempFolderName
		start_instance_pair $architec $zone2 $spotYes $tempFolderName
		remove_temp_files "spots/$architec/$zone2/$tempFolderName" "$zone2"

	else
		mkdir -p spots/$architec/$zone1/$tempFolderName
		mkdir -p spots/$architec/$zone2/$tempFolderName
		start_instance_pair $architec $zone1 $spotYes $tempFolderName &
		start_instance_pair $architec $zone2 $spotYes $tempFolderName
		remove_temp_files "spots/$architec/$zone1/$tempFolderName" "$zone1" 
		remove_temp_files "spots/$architec/$zone2/$tempFolderName" "$zone2"

	fi


}


create_data(){

	#Get region list
	if [ -e jsons/regions.json ]
	then
		sleep .0001
	else
		echo "[*] Loading regions ..."
		aws ec2 describe-regions > jsons/regions.json
	fi

	regionSize=$(jq '.Regions | length' jsons/regions.json)
	typesSize=$(cat jsons/instance-types.json | jq '.types | length')

	gen_all_folders $regionSize $typesSize
	load_prices $regionSize $typesSize
	get_min_price
}

#Start either 2 normal instances for speciefied region or request a spot
#1 architecure type - eg. t2.micro
#2 zone - eg. eu-west-1
#3 start spot? - eg. yes or no
#4 temporary folder name that will be deleted after work
start_instance_pair(){
	architec=$1
	zone=$2
	spotYes=$3
	tempFolderName=$4
	if [[ $spotYes = "yes" ]];
	then
		runOnNormalInstance=0
	else
		runOnNormalInstance=1
	fi


	prepare_spec_file "spots/$architec/$zone/$tempFolderName" "$zone" "spots/$architec/$zone/"


	if [ $runOnNormalInstance = 1 ]
	then
		start_normal_instance $zone "spots/$architec/$zone/$tempFolderName" 1 &
		wait1=$!
		start_normal_instance $zone "spots/$architec/$zone/$tempFolderName" 2 &
		wait2=$!
		wait $wait1
		wait $wait2
	else
		start_spot_for_region $zone "spots/$architec/$zone/$tempFolderName"
	fi

	instanceId=$(cat spots/$architec/$zone/$tempFolderName/1.txt)
	instanceId2=$(cat spots/$architec/$zone/$tempFolderName/2.txt)

	dnsName=$(aws --region=$zone ec2 describe-instances --instance-ids $instanceId --query Reservations[0].Instances[0].PublicDnsName)
	dnsName2=$(aws --region=$zone ec2 describe-instances --instance-ids $instanceId2 --query Reservations[0].Instances[0].PublicDnsName)
	wait
	dnsName=${dnsName//\"}
	dnsName2=${dnsName2//\"}
	mkdir -p "spots/$architec/@results"
	./startVm2Vm.sh "$dnsName" "$dnsName2" "$(cat spots/$architec/$zone/$tempFolderName/keyName.txt)" "spots/$architec/$zone/$tempFolderName/" "$zone" "spots/$architec" "$architec"
	aws --region=$zone ec2 terminate-instances --instance-ids $instanceId
	aws --region=$zone ec2 terminate-instances --instance-ids $instanceId2
}

#generate all necessary folders for every instance-type + its regions
gen_all_folders(){
	regionSize=$1

	typeSize=$2
	for (( i=0; i<$typeSize; i++ ))
	do
		insName=$(cat jsons/instance-types.json | jq -r .types[$i].type)
		mkdir -p spots/"$insName"
		gen_region_folder $regionSize $insName &
		folderCreater[$i]=$!
	done

	for i in "${folderCreater[@]}"
	do
		wait $i
	done
}

#Load spot-instance prizes for every type
load_prices(){
	regionSize=$1
	typeSize=$2
	rm -f temp.txt
	echo "[*] Gathering prices in every region for all instance-types..."
	for (( i=0; i<$typeSize; i++ ))
	do
		for (( j=0; j<$regionSize; j++ ))
		do
			tempIname=$(cat jsons/instance-types.json | jq -r .types[$i].type)
			gather_prices $tempIname $j &
		done
		echo "[*] Waiting for $tempIname to finish..."
		waitJobs
		wait_for_gathering
	done
	

}
#subroutine for load_prices
gather_prices()
{
	j=$2
	tempReg=$(cat jsons/regions.json | jq -r .Regions[$j].RegionName)
	tempIname=$1
	aws --region=$tempReg ec2 describe-spot-price-history --instance-types $tempIname > "spots/$tempIname/$tempReg/$tempIname-prices-$tempReg.json" &
	printf '%s\n' $! >> temp.txt
}
#different way to wait for sub-processes can also achieved by calling waitJobs
wait_for_gathering()
{
	lineSize=$(wc -l < temp.txt)
	x=0
	while IFS='' read -r line || [[ -n "$line" ]]; do
		lineSize=$(wc -l < temp.txt)
		while [ -e /proc/$line ]
		do
			sleep 1
		done
		let x=$x+1
	done < temp.txt
}

#Java program that crawls through the generated folders and finds the 2 cheapest regions for an instance-type
get_min_price()
{
	echo "[*] Searching minimum price for all instance-type in each region..."
	java -jar findMin.jar "$(pwd)" "jsons/instance-types.json" "jsons/regions.json"
}

gen_region_folder()
{
	for (( i=0; i<$1; i++ ))
	do
		mkdir -p "spots/$2/$(cat jsons/regions.json | jq -r .Regions[$i].RegionName)"
	done

}

alternative_start_for_unavail_spot()
{
	region=$1
	path=$2

	echo "[*] Error for $path in $region Calling normal instance instead..."
	start_normal_instance $region "$path" 1 &
	wait1=$!
	start_normal_instance $region "$path" 2 &
	wait2=$!
	wait $wait1
	wait $wait2

}

#Request a spot instance - wait for fulfillment - wait until both are running
start_spot_for_region(){
	region=$1
	path=$2

	type=$(cat "$path/Specification.json" | jq -r .InstanceType)
	echo "[*] Requesting Spot-Instance for $type in $region..."
	aws --region=$region ec2 request-spot-instances --instance-count 2 --type "one-time" --launch-specification "file://$path/Specification.json" --spot-price "0.50" > "$path/spot-req.json"
	reqId=$(cat $path/spot-req.json | jq -r .SpotInstanceRequests[0].SpotInstanceRequestId)
	reqId2=$(cat $path/spot-req.json | jq -r .SpotInstanceRequests[1].SpotInstanceRequestId)
	
	if [[ $reqId = "" ]] || [[ $reqId2 = "" ]];
	then 
		alternative_start_for_unavail_spot $region $path
		return
	fi

	while true; do
		aws --region=$region ec2 describe-spot-instance-requests --spot-instance-request-ids $reqId > "$path/req-status.json" &
		aws --region=$region ec2 describe-spot-instance-requests --spot-instance-request-ids $reqId2 > "$path/req-status2.json"
		reqStatus=$(cat $path/req-status.json | jq -r .SpotInstanceRequests[0].Status.Code)
		reqStatus2=$(cat $path/req-status2.json | jq -r .SpotInstanceRequests[0].Status.Code)
		echo "[*] Request-status for $type in $region: $reqStatus | $reqStatus"
		if [ $reqStatus = "fulfilled" -a $reqStatus2 = "fulfilled" ]; 
		then
			break
		elif [ $reqStatus != *"pending"* -a $reqStatus2 != *"pending"* ];
		then
			alternative_start_for_unavail_spot $region $path
			return
		else
			sleep 2
		fi
		
	done


	instanceId=$(cat "$path/req-status.json" | jq -r .SpotInstanceRequests[0].InstanceId)
	echo $instanceId > $path/1.txt
	instanceId2=$(cat "$path/req-status2.json" | jq -r .SpotInstanceRequests[0].InstanceId)
	echo $instanceId2 > $path/2.txt
	
	if [[ $instanceId != *"i-"* ]] || [[ $instanceId2 != *"i-"* ]];
	then 
		alternative_start_for_unavail_spot $region $path
		return
	fi
	
	echo "[*] Got spot-instance for $type in $region with id: $instanceId | $instanceId2"
	while true; do
		status=$(aws --region=$region ec2 describe-instances --instance-ids $instanceId --query Reservations[0].Instances[0].State.Name)
		status2=$(aws --region=$region ec2 describe-instances --instance-ids $instanceId2 --query Reservations[0].Instances[0].State.Name)
		echo "[*] Instance-status for $type in $region: $status | $status2" 
		if [ $status = "\"running\"" -a $status2 = "\"running\"" ]; 
		then
			break
		fi
		sleep 2
	done
}

#Start a normal instance
#3 call with a unique filename so the instance id can be extracted from it
start_normal_instance(){
	region=$1
	path=$2
	filename=$3

	type=$(cat "$path/Specification.json" | jq -r .InstanceType)
	amiID=$(cat "$path/Specification.json" | jq -r .ImageId) 
	keyName=$(cat "$path/Specification.json" | jq -r .KeyName) 
	secGroupID=$(cat "$path/Specification.json" | jq -r .SecurityGroupIds[0]) 
	itype=$(cat "$path/Specification.json" | jq -r .InstanceType)

	instanceId=$(aws --region=$region ec2 run-instances --image-id $amiID --count 1 --instance-type $itype --key-name $keyName --security-group-ids $secGroupID --query Instances[0].InstanceId)
	instanceId=${instanceId//\"}
	echo "[*] Got instance for $type in $region with id: $instanceId"
	while true; do
		status=$(aws --region=$region ec2 describe-instances --instance-ids $instanceId --query Reservations[0].Instances[0].State.Name)
		echo "[*] Instance-status for $type in $region: $status" 
		if [ $status = "\"running\"" ]; 
		then
			break
		fi
		sleep 2
	done

	echo $instanceId > "$path/$filename.txt"



}

#Specification.json needed for a spot-request
prepare_spec_file(){
	path=$1
	region=$2
	sourcefilePath=$3

	cat jsons/template.json > "$path/Specification.json"

	instanceType=$(cat $sourcefilePath/best-$region.json | jq -r .InstanceType)
	cat "$path/Specification.json" | jq --arg instanceType "$instanceType" '.InstanceType = $instanceType ' > "$path/dump.json"
	cat "$path/dump.json" > "$path/Specification.json"

	avZone=$(cat $sourcefilePath/best-$region.json | jq -r .AvailabilityZone)
	cat "$path/Specification.json" | jq --arg avZone "$avZone" '.Placement.AvailabilityZone = $avZone '  > "$path/dump.json"
	cat "$path/dump.json" > "$path/Specification.json"

	aws --region=$region ec2 describe-images --filters "Name=name,Values=amzn-ami-hvm-2018.03.0.20180508-x86_64-gp2" > "$path/image.json"
	imgId=$(cat $path/image.json | jq -r .Images[0].ImageId)
	cat "$path/Specification.json" | jq --arg imgId "$imgId" '.ImageId = $imgId '   > "$path/dump.json"
	cat "$path/dump.json" > "$path/Specification.json"

	prepare_sec_group "$path" $region
	prepare_key "$path" $region

}

#For every region needed: working security group with SSH access on port 22
prepare_sec_group(){
	path=$1
	region=$2
	aws --region=$region ec2 create-security-group --group-name "temp-time-mes" --description "temporary sec group" > "$path/tempSecGroup.json"
	if [ $? != 255 ]
	then
		cat "$path/tempSecGroup.json" > "$path/secGroup.json" 
		echo "[*] Creating new Security-Group in $region..."
	else
		echo "[*] Getting existing Security-Group in $region..."
		aws --region=$region ec2 describe-security-groups --query 'SecurityGroups[0]' --group-names "temp-time-mes" > "$path/tempSecGroup.json"
		cat "$path/tempSecGroup.json" > "$path/secGroup.json"
	fi
	aws --region=$region ec2 authorize-security-group-ingress --group-name "temp-time-mes" --protocol tcp --port 22 --cidr 0.0.0.0/0

	secGroupId=$(cat $path/secGroup.json | jq -r .GroupId)

	cat "$path/Specification.json" | jq --arg secGroupId "$secGroupId" '.SecurityGroupIds[0] = $secGroupId ' > "$path/dump.json"
	cat "$path/dump.json" > "$path/Specification.json"

}

#Remove all temp files created and delte key from aws
remove_temp_files(){
	path="$1"
	region="$2"
	keyName=$(cat $path/keyName.txt)
	aws --region=$region ec2 delete-key-pair --key-name $keyName
	rm -rf $path
	rm -f temp.txt
}

#Prepare a new key for an instance launch
prepare_key(){
	path=$1
	region=$2
	
	while true; do
		keyName="$(date +%s%N| sha1sum | tr -cd '[[:alnum:]]')"
		echo $keyName > $path/keyName.txt
		key=$(aws --region=$region ec2 create-key-pair --key-name $keyName)
		if [ $? != 255 ]
		then
			echo "[*] Creating new Key in $region..."
			break
		fi
	done

	echo "$key" > "$path/key.json"
	cat "$path/key.json" | jq -r .KeyMaterial  > "$path/$keyName.pem"
	chmod 400 $path/$keyName.pem

	pathToKey="$path/$keyName"
	cat "$path/Specification.json" | jq --arg keyName "$keyName" '.KeyName = $keyName'  > "$path/dump.json"
	cat "$path/dump.json" > "$path/Specification.json"
}

#Check what version of JQ to use
#JQ - a Jason parser
if [[ "$OSTYPE" == "linux-gnu" ]]; then
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
fi

awsAccesKeyID=$(cat credentials.json | jq -r .awsAccesKeyID)
awsSecretAccessKey=$(cat credentials.json | jq -r .awsSecretAccessKey)

aws configure set aws_access_key_id $awsAccesKeyID
aws configure set aws_secret_access_key $awsSecretAccessKey

spinner(){
	PID=$1
	i=1
	sp="/-\|"
	echo -n ' '
	while [ -d /proc/$PID ]
	do
		printf "\b${sp:i++%${#sp}:1}"
	done
	printf "\b"
}

waitJobs()
{
	for job in $(jobs -p)
	do
		wait $job
	done
}

cooldown()
{
	waitJobs
	./terminateAll.sh
	echo "[*] Waiting for all instances and requests to shutdown..."
	sleep 60 &
	sleepx=$!
	spinner $sleepx &
	wait $!
}

#Can start up to 20 instances - aws doesnt offer t2 spot-instances that easily so start normal ones
#For 5 types there are 4 instaces started on each one -> 20 total so we are fine
phase1()
{	echo "[*] Starting measurement for t2 family..."
	main "t2.micro" "no" &
	main "t2.small" "no" &
	main "t2.medium" "no" &
	main "t2.large" "no" &
	main "t2.xlarge" "no" &
	cooldown
	
}

#Restricted to a handfull per instance-type - do measurements for first region
phase2()
{
	echo "[*] Starting measurement for m5.large and m5.xlarge in 1st zone..."
	main "m5.large" "yes" "1" &
	main "m5.xlarge" "yes" "1" &
	cooldown

}
#Then for 2nd region
phase3()
{
	echo "[*] Starting measurement for m5.large and m5.xlarge in 2nd zone..."
	main "m5.large" "yes" "2" &
	main "m5.xlarge" "yes" "2" &
	cooldown


}
#Same problem here
phase4()
{
	echo "[*] Starting measurement for c5.large, c5.xlarge and m5.2xlarge in 1st zone..."
	main "c5.large" "yes" "1" &
	main "c5.xlarge" "yes" "1" &
	main "m5.2xlarge" "yes" "1" &
	cooldown

}
#Final phase
phase5()
{
	echo "[*] Starting measurement for c5.large, c5.xlarge and m5.2xlarge in 2nd zone..."
	main "c5.large" "yes" "2" &
	main "c5.xlarge" "yes" "2" &
	main "m5.2xlarge" "yes" "2" &
	cooldown
}

setup()
{
	create_data
	waitJobs
}

echo "start: $(date)" > totalTime
setup
phase1
phase2
phase3
phase4
phase5
java -jar costPerformance.jar "$(pwd)"
echo "end: $(date)" >> totalTime
echo ""
column -s, -t < cost-performance.csv


