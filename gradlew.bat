@if [ -f "$0" ]; then
  eval "$0"; exit
@else
  echo "This script must be run from the same directory as the Gradle wrapper script."
  exit 1
@endif