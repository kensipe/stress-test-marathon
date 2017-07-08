library("ggplot2")
source("common.R")

Sys.setenv(TZ='UTC')
## file <- Sys.getenv("FILE", unset = "./all.tsv")
out_file <- Sys.getenv("OUTFILE", unset = "./output.pdf")

read_the_thing <- function (label) {
    d <- read.table(paste("../output/step-", label, ".tsv", sep=""), sep = "\t", header = T)
    d$run <- rep(label, nrow(d))
    return(d)
}

d <- rbind(
    read_the_thing("1-launch-500-good-hc"),
    read_the_thing("2-launch-400-bad-hc"),
    read_the_thing("3-good-hc-to-2"),
    read_the_thing("4-good-hc-to-3"),
    read_the_thing("5-good-hc-to-2-no-poll"),
    read_the_thing("6-good-hc-to-3-no-poll"))

d$run <- as.factor(d$run)
d$date <- as.POSIXct( d$date, origin="1970-01-01")
f <- d[d$time > 0.0,]

render.twice(function () {
    return
    (
        ggplot(f) +
        labs(x = "time", y = "response time") +
        scale_y_continuous() +
        geom_point(size = 3, aes(x = date, y = time, colour = run))
    )
}, tools::file_path_sans_ext(out_file), width = 12, height = 6)


render.twice(function () {
    return
    (
        ggplot(f) +
        labs(x = "time", y = "response time") +
        scale_y_continuous() +
        geom_boxplot(aes(x = run, y = time, colour = run))
    )
}, (paste(tools::file_path_sans_ext(out_file),"_box", sep="")), width = 12, height = 6)

