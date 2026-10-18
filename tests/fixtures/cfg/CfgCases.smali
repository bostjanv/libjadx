# Owned DEX CFG oracle. Offsets below are manually counted 16-bit code units.
.class public abstract Lprobe/CfgCases;
.super Ljava/lang/Object;

.method public abstract absent()V
.end method

.method public static straight()I
    .registers 1
    const/16 v0, 17                 # 0
    return v0                      # 2
.end method

.method public static jump()I
    .registers 1
    const/4 v0, 1                  # 0
    goto :target                   # 1 -> 5
    const/16 v0, 99                # 2 unreachable
    return v0                      # 4 unreachable
    :target
    const/16 v0, 23                # 5
    return v0                      # 7
.end method

.method public static branchLoop(I)I
    .registers 2
    const/4 v0, 0                  # 0
    :head
    if-lez p0, :end                 # 1 -> 13
    if-eqz v0, :left                # 3 -> 8
    add-int/lit8 v0, v0, 3          # 5
    goto :join                     # 7 -> 10
    :left
    add-int/lit8 v0, v0, 7          # 8
    :join
    add-int/lit8 p0, p0, -1         # 10
    goto :head                     # 12 -> 1
    :end
    return v0                      # 13
.end method

.method public static packed(I)I
    .registers 2
    const/4 v0, 0                  # 0
    packed-switch p0, :data         # 1 -> payload 14 (not executable)
    const/16 v0, -11               # 4 default
    return v0                      # 6
    :four
    const/16 v0, 23                # 7 key 4
    return v0                      # 9
    :five
    const/16 v0, 41                # 10 key 5
    return v0                      # 12
    :data                          # assembler adds alignment nop at 13
    .packed-switch 4               # 14, size 8
        :four
        :five
    .end packed-switch
.end method

.method public static sparse(I)I
    .registers 2
    const/4 v0, 0                  # 0
    sparse-switch p0, :data         # 1 -> payload 14
    const/16 v0, -11               # 4 default
    return v0                      # 6
    :negative
    const/16 v0, 23                # 7 key -5
    return v0                      # 9
    :large
    const/16 v0, 41                # 10 key 1000
    return v0                      # 12
    :data
    .sparse-switch                 # 14, size 10
        -5 -> :negative
        1000 -> :large
    .end sparse-switch
.end method

.method public static array()I
    .registers 2
    const/4 v0, 2                  # 0
    new-array v0, v0, [I            # 1
    fill-array-data v0, :data       # 3 -> payload 10
    const/4 v1, 1                  # 6
    aget v0, v0, v1                # 7
    return v0                      # 9
    :data
    .array-data 4                  # 10, size 8; valid initialized array
        13
        29
    .end array-data
.end method

.method public static catches(Ljava/lang/RuntimeException;[I)I
    .registers 4
    const/4 v0, 1                  # 0
    :try_start
    array-length v0, p1            # 1, may throw NPE
    if-eqz p0, :normal              # 2 -> 5
    throw p0                       # 4, explicit throw
    :try_end
    :normal
    return v0                      # 5
    :typed
    move-exception v1              # 6
    const/16 v0, -17               # 7
    return v0                      # 9
    :all
    move-exception v1              # 10
    const/16 v0, -29               # 11
    return v0                      # 13
    .catch Ljava/lang/RuntimeException; {:try_start .. :try_end} :typed
    .catchall {:try_start .. :try_end} :all
.end method
