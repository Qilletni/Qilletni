album alb = "Porcelain" album by "Peach PRC"

collection porcelain = alb.toCollection()
collection aTear = ("A Tear In The Fabric of Life" album by "Knocked Looe").toCollection()

collection union = porcelain.union(aTear)

print(union.toVerboseString())
