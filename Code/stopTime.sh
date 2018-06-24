#!/bin/bash

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

dnsName=$1
keyName=$2
zone=$3
architec=$4
while true; do
	scp -i $keyName.pem -o StrictHostKeyChecking=no 1.dat ec2-user@$dnsName:~
	if [ "$?" = "1" ];
	then
		echo "[*] Filecopy failed! Trying again..."
		sleep 2
	else
		break
	fi
done

declare -A matrix
for (( j=0; j<5; j++ ))
do
	for (( i=0; i<=6; i++ ))
	do
		startTime=$(date +%s%N)
		echo "[$architec][$zone] Run $j: Transfering file $i.dat ..."
		scp -i $keyName.pem -o StrictHostKeyChecking=no $i.dat ec2-user@$dnsName:~/fromVM &
		loop=$!
		spinner $loop &
		wait $!
		endTime=$(date +%s%N)	
		totalTime=$(expr $endTime - $startTime)
		totalTime=$(expr $totalTime / 1000000)
		matrix[$i,$j]=$totalTime

	done
done


for (( i=0; i<=6; i++ ))
do
	temp=0
	for (( j=0; j<5; j++ ))
	do
		let temp=$temp+${matrix[$i,$j]}
	done
	let temp=$temp/5
	res[$i]=$temp
done

echo -e "{\n\t\"Files\":[\n\t" > "$zone-VM1toVM2.json"
for (( i=0; i<=6; i++ ))
do
	totalTime2=${res[$i]}
	si=$(stat -c%s $i.dat)
	speed=$(echo $si $totalTime2 | awk '{ printf "%f", ($1/$2)/1000 }')
	echo -e "\t\t{\n\t\t\"name\":\"$i.data\",\n\t\t\"size\":\"$si\",\n\t\t\"transferTime\":\"$totalTime2\",\n\t\t\"speedInMBpS\":\"$speed\"\n\t\t}," >> "$zone-VM1toVM2.json"
done
echo -e "{}\n],\n" >> "$zone-VM1toVM2.json"
cat $zone-VM1toVM2.json


