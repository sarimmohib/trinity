# Release builds run through R8 (see app/build.gradle.kts). Activities, services and
# receivers named in AndroidManifest.xml are kept automatically, and Compose and
# AndroidX ship their own consumer rules, so nothing extra is needed yet.
#
# If a release build misbehaves where the debug build does not, the cause is almost
# always something reached by name at runtime (reflection). Add a -keep rule for that
# class here rather than turning minification off.
