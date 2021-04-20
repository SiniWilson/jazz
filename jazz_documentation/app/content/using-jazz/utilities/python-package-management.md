
+++
date = "2017-11-27T14:00:00-07:00"
draft = true
title = "Python Package Management"
author = "Deepak Babu"
weight = 14

[menu.main]
Name = "Python Package Management"
parent = "utilities"

pre = "All required packages must be placed in requirements.txt and requirements-dev.txt. These files will be used with pip to install all required packages in a [virtual environment] (https://python-guide-pt-br.readthedocs.io/en/latest/dev/virtualenvs/)"

+++
<!-- Add a short description in the pre field inside menu

Python Package Management
========================= -->

All required packages must be placed in requirements.txt and
requirements-dev.txt. These files will be used with pip to install all
required packages in a [virtual
environment](https://python-guide-pt-br.readthedocs.io/en/latest/dev/virtualenvs/)
for testing and deployment. See [this
site](https://pip.readthedocs.io/en/1.1/requirements.html) for
documentation for how to format requirements file.

Even if your code does not import certifi, place certifi in the
requirements file. certifi is a bundle of public CA root certificates
that any library using SSL/TLS will expect to find.

