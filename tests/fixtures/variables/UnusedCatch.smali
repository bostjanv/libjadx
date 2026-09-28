# Owned DEX fixture: an optimized-away exception value has no move-exception.
.class public Lprobe/UnusedCatch;
.super Ljava/lang/Object;

.method public static unusedCatch(I)I
    .registers 3
    :try_start
    const-string v0, "42"
    invoke-static {v0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I
    move-result v0
    add-int v0, v0, p0
    :try_end
    return v0
    .catch Ljava/lang/NumberFormatException; {:try_start .. :try_end} :handler
    :handler
    return p0
.end method
