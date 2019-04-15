# Python 3 Port Utilities
Scripts to help migrate us to Python 3.

## To run scripts
1. Copy the script into the relevant repository.
1. `chmod +x my_script.py`
1. `echo "my_script.py" >> .git/info/exclude` to keep it out of Git tracking.
1. `./my_script.py --help` for usage notes.

## To develop new scripts
Developing new scripts is easy because most of the scaffolding already exists. 

You'll have to pick and choose the behavior you want to include.

* Operating on Foursquare modules? 
  * Look at 
  [`foursquare/futurize.py`](https://github.com/foursquare/python3-port-utilities/blob/master/foursquare/futurize.py), 
  [`foursquare/iteritems.py`](https://github.com/foursquare/python3-port-utilities/blob/master/foursquare/iteritems.py), 
  [`foursquare/sets.py`](https://github.com/foursquare/python3-port-utilities/blob/master/foursquare/sets.py), 
  or [`foursquare/strings.py`](https://github.com/foursquare/python3-port-utilities/blob/master/foursquare/strings.py) 
  for how they handle Paths navigation and the `--luigi` and `--fsqio` args.
* Calling Pants goals? 
  * Look at 
  [`foursquare/futurize.py`](https://github.com/foursquare/python3-port-utilities/blob/master/foursquare/futurize.py), 
  [`pants/futurize.py`](https://github.com/foursquare/python3-port-utilities/blob/master/pants/futurize.py), 
  or [`pants/test.py`](https://github.com/foursquare/python3-port-utilities/blob/master/pants/test.py) 
  for how they call `./fs` and `./pants`.
* Making regex-based change?
  * Look at 
  [`foursquare/iteritems.py`](https://github.com/foursquare/python3-port-utilities/blob/master/foursquare/iteritems.py), 
  [`foursquare/sets.py`](https://github.com/foursquare/python3-port-utilities/blob/master/foursquare/sets.py), 
  or [`foursquare/strings.py`](https://github.com/foursquare/python3-port-utilities/blob/master/foursquare/strings.py) 
  for how they use Regex and the Ripgrep tool.

Start by copying the file most aligned to the behavior you want, and then modify as necessary.
