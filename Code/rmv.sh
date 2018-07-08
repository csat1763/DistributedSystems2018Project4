#!/bin/bash
#rm -rf spots
#rm -f cost-performance.csv
#rm -f totalTime
reqStatus="pending"
reqStatus2="x"
a="$([[ $reqStatus != *"pending"* ]])"
b="$([[ $reqStatus2 != *"pending"* ]])"
if [[ $reqStatus != *"pending"* ]] && [[ $reqStatus2 != *"pending"* ]];
then
echo FAIL
else echo both ok
fi

if [[ $reqStatus != *"pending"* ]];
then
echo FAIL
else echo both ok
fi

echo "$a $b"