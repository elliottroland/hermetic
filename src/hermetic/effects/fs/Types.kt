package hermetic.effects.fs

typealias GlobalFileSystem = FileSystem<Lifespan.Persistent, Scope.Global>
typealias RestrictedFileSystem = FileSystem<Lifespan.Persistent, Scope.Restricted>
typealias EphemeralGlobalFileSystem = FileSystem<Lifespan.Ephemeral, Scope.Global>
typealias EphemeralRestrictedFileSystem = FileSystem<Lifespan.Ephemeral, Scope.Restricted>
