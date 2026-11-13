album alb = "Porcelain" album by "Peach PRC"

// Two ways of doing the same thing
collection porcelain = alb.toCollection()
//collection aTear = ("A Tear In The Fabric of Life" album by "Knocked Looe").toCollection()
collection aTear = collection("A Tear In The Fabric of Life" album by "Knocked Looe")

collection union = porcelain.union(aTear)

print(union.toVerboseString())
