#!/bin/bash


function print_usage {
  echo "install.sh MOVIE_DIRECTORY"
}


function install {
  mkdir -p /opt/movie-helper
  cp movie-helper.jar /opt/movie-helper/
  echo "directory.path=$1" > /opt/movie-helper/config.properties
  cp movie-helper.service /lib/systemd/system/
  systemctl daemon-reload
}


if [[ $# -ne 1 ]]; then
  print_usage
  exit 1
fi


install "$1"

