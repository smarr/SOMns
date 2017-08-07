Release Checklist
=================

This list is supposed to make sure we do not forget any important steps during 
release.

1. Check and update issue tracker for issues associated with current milestone

2. Update CHANGELOG.md with all elements missing
   A useful tool is the `github_changelog_generator`
   
   It can be used like this:
   
```
sudo gem install github_changelog_generator
github_changelog_generator -u smarr -p SOMns --token ${GITHUB_TOKEN}
```

3. Make sure all desired changes are part of the `dev` branch.

4. Prepare a release on GitHub with: https://github.com/smarr/SOMns/releases/new
   The content is normally just the last part of the CHANGELOG.md

5. If everything looks good, tag the release using the GitHub feature

6. Push the commit that is tagged as release to the `master` branch.

7. Announce the release
  - https://groups.google.com/forum/#!forum/som-dev
  - https://twitter.com/SOM_VMs
