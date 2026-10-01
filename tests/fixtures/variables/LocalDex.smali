# Owned direct DEX shapes with intentional sequential register reuse.
.class public Lprobe/LocalDex;
.super Ljava/lang/Object;
.field public static sink:I
.field public static wideSink:J

.method public static straight(I)I
    .registers 3
    mul-int/lit8 v0, p0, 0x7
    sput v0, Lprobe/LocalDex;->sink:I
    add-int/lit8 v1, v0, 0x3
    return v1
.end method

.method public static reuse(I)I
    .registers 3
    add-int/lit8 v0, p0, 0xb
    sput v0, Lprobe/LocalDex;->sink:I
    add-int/2addr v0, v0
    sput v0, Lprobe/LocalDex;->sink:I
    mul-int/lit8 v0, p0, 0xd
    sget v1, Lprobe/LocalDex;->sink:I
    add-int/2addr v0, v1
    sput v0, Lprobe/LocalDex;->sink:I
    return v0
.end method

.method public static merged(I)I
    .registers 3
    if-lez p0, :negative
    add-int/lit8 v0, p0, 0x1
    goto :join
    :negative
    add-int/lit8 v0, p0, -0x1
    :join
    const/4 v1, 0x0
    :loop
    const/4 p0, 0x3
    if-ge v1, p0, :done
    add-int/2addr v0, v1
    add-int/lit8 v1, v1, 0x1
    goto :loop
    :done
    sput v0, Lprobe/LocalDex;->sink:I
    return v0
.end method

.method public static wide(JD)J
    .registers 8
    const-wide/16 v0, 0x49
    mul-long/2addr v0, p0
    sput-wide v0, Lprobe/LocalDex;->wideSink:J
    double-to-long v2, p2
    add-long/2addr v2, v0
    return-wide v2
.end method

.method public static object(Ljava/lang/String;)Ljava/lang/String;
    .registers 3
    invoke-virtual {p0}, Ljava/lang/String;->trim()Ljava/lang/String;
    move-result-object v0
    invoke-virtual {v0}, Ljava/lang/String;->length()I
    move-result v1
    sput v1, Lprobe/LocalDex;->sink:I
    return-object v0
.end method
