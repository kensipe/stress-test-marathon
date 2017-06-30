library("ggplot2")

file <- Sys.getenv("FILE", unset = "./step-6-good-hc-to-3-no-poll.log.tsv")

d <- read.table(file, sep = "\t", header = T)

f <- d[d$time > 0.0,]


pdf(paste(file, "pdf", sep="."), width = 12, height = 6)
(
    ggplot(f, aes(x = app.id, y = time)) +
    labs(x = "apps") +
    scale_y_continuous() +
    geom_point(size = 3) +
    geom_smooth(method = "lm")
)
dev.off()
