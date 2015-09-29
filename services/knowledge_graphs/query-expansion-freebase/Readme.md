FQE - Freebase query expansion for open vocabulary recognition
=========================================================

This project is part of the open vocabulary object retrieval project presented at http://openvoc.berkeleyvision.org.
Users often refer to objects by using non-category terms, like "please robot, pass me the kellogs". The idea of the freebase
query expansion is to search for these terms and substitute them with their definitions that are likely containing category-terms.
This expansion might allow subsequent systems to identify category terms and relate them to learned category models.

Author: Erik Rodner (University of Jena)

Installation
=====================================

1. Get an API key for the freebase framework
2. Create a file in your home directory called ``.freebase_config'' with the following content

    [main]
    apikey = ...
    filter = (all type:/food/food)

Usage
====================================
Example call: ``python freebase-extend-descriptions.py --src test-src.json --dst /dev/stdout''

Result:
        
        {
            "test1": [
                "please give me the kellogs box", 
                "where is my ( mountain dew : Mello Yello is a high-caffeinated, citrus-flavored soft drink produced and distributed by The Coca-Cola Company which was introduced on March 1, 1979 to compete with Pepsi's Mountain Dew )?", 
                "i need my cap'n'crunch, now!"
            ]
        }
