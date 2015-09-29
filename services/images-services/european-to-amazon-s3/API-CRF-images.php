<?php
/**
 * Retrieve all HQ images from Europeana
 */
ob_start();
//set output as plain text, for easy copying.
header('Content-type: text/javascript');
error_reporting(E_ALL);
ini_set('display_errors','On');
set_time_limit(0);
ini_set("allow_url_fopen", true);
$base   = 'http://beta.europeana.eu/v2/'; // host name of the API
$key = "xxx";
$query  = '*:*'; // search query
$total  = 5000; // has to be dividable by 50, this number determines how much objects you want to retrieve (media items may be more!)
// Image sizes
$sizes  = array('medium','large','extra_large'); // images sizes you want to return (small, medium, large, extra_large)
$imageSizes = '';
foreach ($sizes as $size) {
    $imageSizes .= '&qf=IMAGE_SIZE:' . $size;
}
// Licenses
$licenses = array("*mark*", "*zero*", "*by/*", "*by-sa/*"); // , "*by-nc-sa\/*", "*by-nc\/*"
$licenseQuery = '';
foreach ($licenses as $license) {
    $licenseQuery .= '&qf=RIGHTS:' . $license;
}
$metadataSet = array();
// Construct search query, we're sending out new requests per 50 objects
for ($x=1; $x<=$total; $x+=50) {
	$request = $base . 'search.json?start='.$x.'&rows=50&query='.$query.$imageSizes.'&wskey=' . $key . "&profile=portal+rich"; //&facet=description&facet=who
    $result = file_get_contents($request);
    // Iterate through results
    $results = json_decode($result, true);
    foreach ($results['items'] as $item) {
		$id = "";
		$institution = "";
		$institution_link = "";
		$url = "";
		$license = "";
		$source = "";
		$title = "";
		$creator = "";
		$description = "";
		
		if (array_key_exists('id',$item)) {
			$id = $item['id'];
		} 
		if (array_key_exists('dataProvider',$item)) {
			$institution = $item["dataProvider"][0];
		} 
		if (array_key_exists('edmIsShownAt',$item)) {
			$institution_link = $item["edmIsShownAt"][0];
		} 
		if (array_key_exists('edmIsShownBy',$item)) {
			$url =  $item['edmIsShownBy'][0];
		} 
		if (array_key_exists('rights',$item)) {
			$license = $item['rights'][0];
		} 
		$source = "http://www.europeana.eu/portal/record/" . $id . ".html";
		
		if (array_key_exists('title',$item)) {
			$title = $item['title'][0];
		} 
		if (array_key_exists('dcCreator',$item)) {
			$creator = $item["dcCreator"][0];
		} 
		if (array_key_exists('dcDescription',$item)) {
			$description = $item["dcDescription"][0];
		} 
    	//var_dump($item);exit;
    	$metadataSet[] = array("id" => str_replace("/", "_", $id), 
    							"institution" => $institution, 
    							"institution_link" => $institution_link, 
    							"url" => array($url), 
    							"license" => $license, 
    							"source" => $source, 
    							"title" => $title, 
    							"creator" => $creator, 
    							"description" => $description);
    }
    
}
$metadataSet = json_encode($metadataSet, JSON_PRETTY_PRINT);
echo $metadataSet;
function cleanText($text) {
	return preg_replace( "/\r|\n|\t/", "", $text );
}
?>
