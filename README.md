# Github PR label filter plugin

This plugin enables Github Pull Requests to be filtered on the labels. User can specify a regex to match with pull request labels.
A pull request will only be built if it has one of the matching labels else it won't be built. This is useful in scenarios where 
there a lots of pull requests and not all are intended for CI
