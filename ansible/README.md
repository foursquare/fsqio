### Introduction

This Ansible Role will install the twofishes geocoding application

Tested with a known minimal working Ansible version of 1.9.3.

### Installing roles from ansible galaxy

The ansible playbook that performs the provisioning depends on a few roles provided in the
ansible galaxy.  You can install these rolls with the following command in this directory:

```
ansible-galaxy install -r requirements.txt
```

### Role Variables

* `app_name` - GeoNode project name (default: `geonode`)
* `github_user` - GitHub username that owns the project (default: `GeoNode`)
* `code_repository` - URL to the Code Repository (default: `https://github.com/{{ github_user }}/{{ app_name }}.git`)
* `twofishes_root` - directory of the fsqio git repo
* `twofishes_dir` - directory of the twofishes java source code
* `app_code_data_dir` - directory where twofishes data will be stored
* `app_code_latest_data` - directory of the latest geonames data
* `data_index_file:` - filename of the data zip file to download
* `data_index_url` - url of the data zip file to download


### Setting up a vagrant box

To configure a local development virtual machine, you will need to have virtualbox and vagrant installed.
Note: You may need to change the IP configuration in the VagrantFile to a valid ip on the local network

    $ vagrant up geocoder
    $ vagrant ssh geocoder
    
