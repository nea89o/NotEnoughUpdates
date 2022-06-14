#!/bin/bash
# Copyright (C) 2022 NotEnoughUpdates contributors
#
# This file is part of NotEnoughUpdates.
#
# NotEnoughUpdates is free software: you can redistribute it
# and/or modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation, either
# version 3 of the License, or (at your option) any later version.
#
# NotEnoughUpdates is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with NotEnoughUpdates. If not, see <https://www.gnu.org/licenses/>.
#

featurecommit=$(git log --pretty=%s -1)
mkdir -p ciwork

echo "::group::Gradle build on $featurecommit"
infer capture -- ./gradlew clean test remapJar --no-daemon
echo "::endgroup::"

echo "::group::Infer analyzering on $featurecommit"
infer analyze
echo "::endgroup::"

cp infer-out/report.json ciwork/report-feature.json

git checkout "$GITHUB_BASE_REF"

basecommit=$(git log --pretty=%s -1)

echo "::group::Gradle build on $basecommit"
infer capture --reactive -- ./gradlew test remapJar --no-daemon
echo "::endgroup::"

echo "::group::Infer analyzation on $basecommit"
infer analyze --reactive
echo "::endgroup::"

git switch -

echo "::group::Infer report differential"
infer reportdiff --report-current ciwork/report-feature.json --report-previous infer-out/report.json
jq -r '.[] | select(.severity == "ERROR") | ("::error file="+.file +",line=" +(.line|tostring)+"::" + .qualifier)' <infer-out/differential/introduced.json
jq -r '.[] | select(.severity == "WARNING") | ("::warning file="+.file +",line=" +(.line|tostring)+"::" + .qualifier)' <infer-out/differential/introduced.json
fixcount=$(jq -r "length" <infer-out/differential/fixed.json)
unfixcount=$(jq -r "length" <infer-out/differential/introduced.json)
othercount=$(jq -r "length" <infer-out/differential/preexisting.json)
echo "This PR fixes $fixcount potential bugs, introduces $unfixcount potential bugs. (Total present in feature branch: $((unfixcount + othercount)))" >>$GITHUB_STEP_SUMMARY

echo "::endgroup::"
