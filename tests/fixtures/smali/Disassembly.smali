# Owned fixture: valid disassembly plus deliberately invalid Java register use.
.class public Lprobe/Disassembly;
.super Ljava/lang/Object;

.method public static answer()I
    .registers 1
    const/16 v0, 0x2a
    return v0
.end method

.method public static broken()I
    .registers 1
    return v5
.end method
