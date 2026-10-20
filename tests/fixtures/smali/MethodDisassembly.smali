# Independently owned LibJadx feasibility fixture (Apache-2.0).
.class public abstract Lprobe/MethodDisassembly;
.super Ljava/lang/Object;

.method public static pick(I)I
    .registers 2
    const/16 v0, 0x2a
    add-int/2addr v0, p0
    return v0
.end method

# DEX permits a return-type-only distinction that Java source cannot declare.
.method public static pick(I)J
    .registers 3
    const-wide/16 v0, 0x123
    return-wide v0
.end method

.method public static pick(Ljava/lang/String;J)Ljava/lang/String;
    .registers 3
    return-object p0
.end method

.method public abstract absent(I)I
.end method

.method public static flow(I)I
    .registers 2
    .line 100
    const/16 v0, 0x2a
    packed-switch p0, :table
    :start
    invoke-static {p0}, Lprobe/MethodDisassembly;->pick(I)I
    move-result v0
    :end
    return v0
    :case
    const/4 v0, 0x7
    return v0
    :handler
    move-exception v0
    const/4 v0, -0x1
    return v0
    .catch Ljava/lang/RuntimeException; {:start .. :end} :handler
    :table
    .packed-switch 0x1
        :case
    .end packed-switch
.end method

# Assembler requires distinct payloads; the test retargets the second encoded switch.
.method public static shared(I)V
    .registers 1
    packed-switch p0, :shared_table
    packed-switch p0, :other_table
    return-void
    nop
    :shared_table
    .packed-switch 0x1
        :target
    .end packed-switch
    :other_table
    .packed-switch 0x1
        :target
    .end packed-switch
    :target
    return-void
.end method
