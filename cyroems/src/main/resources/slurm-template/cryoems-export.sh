!/bin/bash
#SBATCH -N 1
#SBATCH -n 1
#SBATCH --cpus-per-task=1
#SBATCH --job-name=cryoems-${movie_name}-export

source ~/.bashrc
source activate cryoems

set -m
