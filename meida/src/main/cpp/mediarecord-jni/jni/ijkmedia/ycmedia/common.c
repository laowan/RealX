/*
 * common.c
 *
 *  Created on: Nov 19, 2014
 *      Author: huangwanzhang
 */

#include "common.h"

#include <libavformat/avformat.h>

//add bhl
#include <sys/time.h>

//add bhl end

// NOTICE that this function is not thread safe
char * mytime(){
	time_t now = time (NULL);
	return ctime(&now);
}

int64_t getcurrenttime_us() {
	struct timeval te;
	gettimeofday(&te, NULL);
	int64_t useconds = te.tv_sec*1000*1000LL + te.tv_usec;
	return useconds;
}

/* warning: not support nested quotations*/
char ** argv_create(const char* cmd, int* count) {
	int i=-1, j=0, argc=0, max_argc = 20480;//265->20480 by bhl
	char **argv = malloc(sizeof(char*) * max_argc);
	int found_quota = 0;
	char last_quota = 0;
	int index1 = 0;
	int index2 = 0;

	memset(argv, 0, sizeof(char*)*max_argc);

//	ALOGD("ffprobe cmd is %s", cmd);

	while(cmd[j] != '\0') {
		if(i>=0 && found_quota == 0 && cmd[j] == ' ') {

			if (j>0 && (cmd[j-1] == '\"' || cmd[j-1] == '\'')) {
				argv[argc] = malloc(j-i-1);
				argv[argc][j-i-2] = '\0';
				memcpy(argv[argc], cmd+i+1, j-i-2);
			}else {
				argv[argc] = malloc(j-i+1);
				argv[argc][j-i] = '\0';
				memcpy(argv[argc], cmd+i, j-i);
			}
			ALOGD("%s", argv[argc]);
			argc++;
			i=-1;
		}

		if (i==-1 && found_quota == 0 && cmd[j] != ' '){
			i=j;
		}

		//find quotation, exclude escape quotation,add by jyq
		if(j > 0 && ((cmd[j]=='\"' && cmd[j-1] != '\\') || (cmd[j] == '\'' && cmd[j-1] != '\\'))) {
			found_quota++;
			if (found_quota == 1) {
				last_quota = cmd[j];
			}
			else if (found_quota%2==0 && last_quota == cmd[j]) {
				found_quota = 0;
				last_quota = 0;
			} else {
				// cover case: -i "xxxx'aaaa.mp4" "xxxx.mp4"
				found_quota--;
			}
		}
		j++;
		if (argc == max_argc) {
			ALOGE("argc(%d) >= max_argc(%d)",  argc,  max_argc);
			max_argc = max_argc*2;
			char ** tmp = malloc(sizeof(char*) * max_argc);
			memcpy((void*)tmp, (void*)argv, sizeof(char*)*j);
			free(argv);
			argv = tmp;
		}
	}

	//support input file path with nested quotation marks,eg:/storage/emulated/0/Movies/"hold me tight" Jun 27, 2018 6.47.15AM.mp4, add by jyq
	int size = 0;
	int start = 0;
	if (i >= 0) {
		if (j > 0 && (cmd[j - 1] == '\"' || cmd[j - 1] == '\'')) {
			size = j - i - 1;
			start = i + 1;
		} else {
			size = j - i + 1;
			start = i;
		}
		argv[argc] = malloc(size);
		memset(argv[argc], 0, size);
		while (index1 < size - 1) {
			if (*(cmd + start + index1) == '\\' && *(cmd + start + 1 + index1) == '\"') {
				index1++;
			}
			argv[argc][index2] = *(cmd + start + index1);
			index1++;
			index2++;
		}
		argv[argc][index2] = '\0';
		argc++;
	}

	ALOGD("argc: %d", argc);

	*count = argc;
	return argv;
}

/* remember to free argv */
void argv_free(char **argv, int argc) {
	int i=0;
	while(i<argc) {
		free(argv[i++]);
	}
	free(argv);
	return;
}

int create_lock(pthread_mutex_t *pmutex) {
	return pthread_mutex_init(pmutex, NULL);
}

int lock(pthread_mutex_t *pmutex) {
	return pthread_mutex_lock(pmutex);
}

int unlock(pthread_mutex_t *pmutex) {
	return pthread_mutex_unlock(pmutex);
}

int destroy_lock(pthread_mutex_t *pmutex) {
	return pthread_mutex_destroy(pmutex);
}


