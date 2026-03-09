# Hermetic code

This is a repository where I'm storing a bunch of ideas and experiements in hermetic code. You can read about this on my Medium: https://medium.com/@elliott.roland/hermetic-code-1-side-effects-make-code-complicated-5c6a6eedeafc

## Building and running

Very bare-bones at the moment:

```
kotlinc -cp /opt/homebrew/Cellar/kotlin/2.3.10/libexec/lib/kotlinx-coroutines-core-jvm.jar -Xcontext-parameters -d build src/* && kotlin -cp build hermetic.effects.RandomKt
```
