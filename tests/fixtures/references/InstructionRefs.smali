# Owned DEX-only instruction-reference probe. Offsets below are 16-bit code units.
.class public abstract Lprobe/InstructionRefs;
.super Ljava/lang/Object;

.field public value:I
.field public static total:I

.method public static entry(Lprobe/InstructionRefs;Lmissing/Api;)V
    .registers 3
    const/16 v0, 0x123
    sget v0, Lprobe/InstructionRefs;->total:I
    sput v0, Lprobe/InstructionRefs;->total:I
    iget v0, p0, Lprobe/InstructionRefs;->value:I
    iput v0, p0, Lprobe/InstructionRefs;->value:I
    invoke-static {}, Lprobe/InstructionRefs;->target()V
    invoke-static {}, Lprobe/InstructionRefs;->target()V
    invoke-static {}, Lmissing/External;->lost()V
    invoke-interface {p1}, Lmissing/Api;->run()V
    invoke-static {p0, p1}, Lprobe/InstructionRefs;->entry(Lprobe/InstructionRefs;Lmissing/Api;)V
    const-class v0, Lmissing/Type;
    invoke-static {v0}, Lprobe/InstructionRefs;->target(Ljava/lang/Class;)V
    return-void
.end method

.method public static target()V
    .registers 0
    return-void
.end method

.method public static target(Ljava/lang/Class;)V
    .registers 1
    return-void
.end method

.method public abstract absent()V
.end method
