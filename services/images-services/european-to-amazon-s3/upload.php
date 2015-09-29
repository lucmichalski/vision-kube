<?php

ob_implicit_flush(true);
//set output as plain text, for easy copying.
header('Content-Type: text/plain');
error_reporting(E_ALL);
ini_set('display_errors','On');
set_time_limit(0);

require "config.php";
require __DIR__ . '/vendor/autoload.php';
use Aws\S3\S3Client;

// Instantiate the S3 client with your AWS credentials
$bucket = 'example.data.hawk.bucket';
$client = S3Client::factory(array('credentials' => $credentials,'region'  => 'eu-central-1'));

// Load data
$data = csvToArray("20150421LargeImages-openlylicensed.csv");
// Load array contain done records from last run
$done = lineToArray("done");

// make sure that you only call a domain once every 30secs.
$domain_cooldown = array();
$cooldownCount = 0;
$cooldown = 30; // cooldown in seconds

// main loop. loop while there is still data
while (count($data) > 0) {
	// Get a random object to avoid hug of death on heritage servers
	$random_index = rand(0, (count($data)-1));
	$object = $data[$random_index];
	
	// load data from object
	$id = $object[0];
	$license = $object[1];
	$location = $object[2];
	
	// If $location has been queried in the last half minute
	if (inCoolDown($location)) {
		// if there are only a few domains, make sure we are not stuck in a inefficient loop.
		$cooldownCount++;
		if ($cooldownCount > 5 && count($domain_cooldown) >0) {
			// Determine shortses sleep neccesary
			asort($domain_cooldown);
			echo "Sleeping for " . time() - $domain_cooldown[0] . " sec.\n";
			sleep(time() - $domain_cooldown[0]);
			$cooldownCount = 0;
		}
		continue;
	}
	
	// transform data into useful formats
	$key = id_to_key($id);
	$filename = $key . createFilename($location); 
	$local = "media/".$filename;
	
	// if already done, remove from data
	if (array_key_exists($id, $done)) {
		echo $id . " already processes.\n";
		unset($data[$random_index]);
		$data = array_values($data);
		continue;
	}
	
	// Try download, continue with next object if downlaod failed.
	if (!downloadFile($location, $local)) {
		continue;
	}
	// Try upload
	if (uploadFile($client, $bucket, $local, $filename, $id, $license)) {
		logToFile ('done', $id);
	}
	// Local file no longer needed.
	deleteFile($local);
	// reset Cooldown count
	$cooldownCount = 0;

	unset($data[$random_index]);
	$data = array_values($data);
}

function csvToArray($filename) {
	$result = [];
	$index = 0;
	$file = fopen($filename, 'r');
	while (($line = fgetcsv($file, 1000, "\t")) !== FALSE) {
		$result[$index]= $line;
		$index++;
	}
	fclose($file);
	return $result;
}

function lineToArray($filename) {
	$result = [];
	$index = 0;
	$file = fopen($filename, 'r');
	if ($file) {
		while (($line = fgets($file)) !== false) {
			$result[trim($line)] = '';
		}
	}
	fclose($file);
	return $result;
}


function createFilename ($url) {
	// First see if there is a filename or a redirect
	$filename = basename($url);
	// if it is a redirect, request filename
	$parts=pathinfo($filename);
 	if (!array_key_exists('extension', $parts)) {
 		$filename = getFilenameFromRedirect($url);
 	}
 	return $filename;
}

function getFilenameFromRedirect($url) {
	$curl = curl_init($url);

	curl_setopt($curl, CURLOPT_HEADER, true);
	curl_setopt($curl, CURLOPT_RETURNTRANSFER, true);
	curl_setopt($curl, CURLOPT_CUSTOMREQUEST, 'HEAD');

	if (($response = curl_exec($curl)) !== false)
	{
		if (curl_getinfo($curl, CURLINFO_HTTP_CODE) == '200')
		{
			$reDispo = '/^Content-Disposition: .*?filename=(?<f>[^\s]+|\x22[^\x22]+\x22)\x3B?.*$/m';
			if (preg_match($reDispo, $response, $mDispo))
			{
			return trim($mDispo['f'],' ";');
			}
		}
	}
	return "";
}

// Download file by $url
function downloadFile($url, $location) {
	try {
		if (!file_exists ( $location ) ) {
			$ch = curl_init();
			curl_setopt($ch, CURLOPT_TIMEOUT_MS, 2000);
			curl_setopt($ch, CURLOPT_URL, $url);
			$fp = fopen($location, 'w');
			curl_setopt($ch, CURLOPT_FILE, $fp);
			curl_exec ($ch);
			curl_close ($ch);
			fclose($fp);
		}
		return true;
	} catch (Exception $e) {
    	logToFile('errors',$e->getMessage());
    	return false;
	}
}

function deleteFile($filename) {
	unlink(realpath($filename));
}

// Replace all slashes with an underscore to make a proper S3 key.
function id_to_key($id) {
	return str_replace("/","_",$id);
}
//
function uploadFile ($client, $bucket, $filename, $key, $id, $license) {
	try {
		// Upload an object by streaming the contents of a file
		// $pathToFile should be absolute path to a file on disk
		$result = $client->putObject(array(
			'Bucket'     => $bucket,
			'Key'        => $key,
			'SourceFile' => $filename,
			'Metadata'   => array(
				'id' => $id,
				'license' => $license
			)
		));

		// We can poll the object until it is accessible
		$client->waitUntil('ObjectExists', array(
			'Bucket' => $bucket,
			'Key'    => $key
		));
		return true;
	}
	catch (Exception $e) {
    	logToFile('errors',$e->getMessage());
    	return false;
	}
}

function logToFile($file, $msg)
{ 
	// write to file
	$fd = fopen($file, "a");
	fwrite($fd, $msg . "\n");
	fclose($fd);
	
	// write to screen
	echo $file . ": " . $msg . "\n";
}

function getDomainFromURL($url) {
	$parse = parse_url($url);
	return $parse['host'];
}

function inCoolDown($url) {
	global $domain_cooldown, $cooldown;
	$domain = getDomainFromURL($url);
	if (array_key_exists($domain, $domain_cooldown)) {
		$coolDownTime = $domain_cooldown[$domain];
		$cooldown = time()-$coolDownTime;
		if ($cooldown >$cooldown) {
			unset($domain_cooldown[$domain]);
			return false;
		} else {
			echo $domain . "is in cooldown for " . $cooldown . " seconds. \n";
			return true;
		}
	} else {
		$domain_cooldown[$domain] =  time();
	}
}

?>