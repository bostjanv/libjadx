# Owned loaded-DEX reader fixture. All offsets are method-relative code units.
.class public Lprobe/InstructionVariants;
.super Lmissing/Base;

.method public variants(J[[ILmissing/Api;)V
    .registers 6
    invoke-virtual {p0}, Lprobe/InstructionVariants;->touch()V
    invoke-super {p0}, Lmissing/Base;->touch()V
    invoke-direct {p0}, Lprobe/InstructionVariants;->privateCall()V
    invoke-static {p1, p2, p3}, Lmissing/External;->wide(J[[I)[J
    invoke-interface {p4}, Lmissing/Api;->run()V
    invoke-virtual/range {p0 .. p0}, Lprobe/InstructionVariants;->touch()V
    invoke-super/range {p0 .. p0}, Lmissing/Base;->touch()V
    invoke-direct/range {p0 .. p0}, Lprobe/InstructionVariants;->privateCall()V
    invoke-static/range {p1 .. p3}, Lmissing/External;->wide(J[[I)[J
    invoke-interface/range {p4 .. p4}, Lmissing/Api;->run()V
    return-void
.end method

.method public touch()V
    .registers 1
    return-void
.end method

.method private privateCall()V
    .registers 1
    return-void
.end method

.method public static fields(J[[I)V
    .registers 3
    sget-wide v0, Lmissing/Fields;->wide:J
    sput-wide v0, Lmissing/Fields;->wide:J
    sget-object v2, Lmissing/Fields;->matrix:[[I
    sput-object v2, Lmissing/Fields;->matrix:[[I
    return-void
.end method

.method public static payloads(I)V
    .registers 2
    :try_start
    packed-switch p0, :packed
    sparse-switch p0, :sparse
    fill-array-data v0, :data
    invoke-static {}, Lmissing/External;->before()V
    :try_end
    :ret
    return-void
    :packed
    .packed-switch 0x0
        :ret
    .end packed-switch
    :sparse
    .sparse-switch
        0x10 -> :ret
    .end sparse-switch
    :data
    .array-data 1
        0x1t
        0x2t
        0x3t
    .end array-data
    :handler
    invoke-static {}, Lmissing/External;->after()V
    return-void
    .catchall {:try_start .. :try_end} :handler
.end method

.method public static broken()V
    .registers 0
    invoke-static {}, Lmissing/External;->recover()V
    return v0
.end method

.method public static excluded(Ljava/lang/invoke/MethodHandle;)V
    .registers 1
    invoke-polymorphic {p0}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, ()V
    invoke-polymorphic/range {p0 .. p0}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, ()V
    invoke-custom {}, site("dynamic", ()V)@Lmissing/Bootstrap;->bootstrap(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
    invoke-custom/range {p0 .. p0}, site("dynamic", ()V)@Lmissing/Bootstrap;->bootstrap(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
    const-method-handle v0, invoke-static@Lmissing/External;->recover()V
    const-method-type v0, ()V
    return-void
.end method
