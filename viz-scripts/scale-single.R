library("ggplot2")
source("common.R")

Sys.setenv(TZ='UTC')
file <- Sys.getenv("FILE", unset = "../artifacts/step-6-good-hc-to-3-no-poll.log.tsv")
out_file <- Sys.getenv("OUTFILE", unset = "output.pdf")

d <- read.table(file, sep = "\t", header = T)

f <- d[d$time > 0.0,]

render.twice(function() {
    return
    (
        ggplot(f, aes(x = app.id, y = time)) +
        labs(x = "apps") +
        scale_y_continuous() +
        geom_point(size = 3) +
        geom_smooth(method = "lm")
    )
}, tools::file_path_sans_ext(out_file), width = 12, height = 6)
