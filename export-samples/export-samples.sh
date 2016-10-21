SVN_REV=$1
#set work dir
CURRENT_DIR=`pwd`
cd ..
WORK_DIR=`pwd`
cd ..
CHK_DIR=`pwd`
cd $CURRENT_DIR

EXPORT_DIR=$WORK_DIR/export-samples
GITHUB_REPO_DIR=/home/github/android-sdk-samples


function export_samples {
    
    echo "export_samples"
    
    if [ -d "$OUTPUT_DIR" ]; then
	rm -Rf $OUTPUT_DIR	
    fi
    
    mkdir $OUTPUT_DIR  
    
    list=`cat export.list`
    for sample in $list
    do
	echo "Sample: $sample"
	export_sample $sample
    done
    
    export_sdk_lib
    export_build_configs
    
}

function export_sample {
    SAMPLE_DIR_NAME=$1
    echo "export_sample SAMPLE_DIR_NAME: $SAMPLE_DIR_NAME"
    DEST_DIR=$OUTPUT_DIR/$SAMPLE_DIR_NAME
    echo DEST_DIR: $DEST_DIR 
    mkdir $DEST_DIR
    export_exclude $WORK_DIR/$SAMPLE_DIR_NAME $DEST_DIR
    remove_fp_api_dep $DEST_DIR    
    
}

function remove_fp_api_dep {
    DEST_DIR=$1
    echo "remove_fp_api_dep DEST_DIR: $DEST_DIR"
    sed -i.bak '/:fp_wcs_api/d' $DEST_DIR/build.gradle    
    rm -f $DEST_DIR/build.gradle.bak
}

function export_exclude {
    CONTENT_DIR=$1
    DEST_DIR=$2
    echo "export_exclude CONTENT_DIR: $CONTENT_DIR	DEST_DIR: $DEST_DIR"
    cd $CONTENT_DIR
    tar cvf tmp.tar --exclude='build' --exclude='libs' --exclude='.gitignore' --exclude='.svn' --exclude='.git' *
    mv tmp.tar $DEST_DIR 
    cd $DEST_DIR 
    tar xvf tmp.tar
    rm -f tmp.tar
}

function set_vars {
    echo "set_vars"
    OUTPUT_DIR="$EXPORT_DIR/output"
    cd $EXPORT_DIR
}

function export_sdk_lib {
    echo "export_sdk_lib"
    SDK_DIR=$OUTPUT_DIR/libs
    mkdir $SDK_DIR
    cp $WORK_DIR/fp_wcs_api/build/outputs/aar/fp_wcs_api-release.aar $SDK_DIR
}

function export_build_configs {
    echo "export_build_configs"
    cp $EXPORT_DIR/build.gradle $OUTPUT_DIR
    
    cp $WORK_DIR/gradle.properties $OUTPUT_DIR    
    cp $WORK_DIR/local.properties $OUTPUT_DIR
    
    cp $WORK_DIR/settings.gradle $OUTPUT_DIR
    sed -i.bak s/\':fp_wcs_api\',//g $OUTPUT_DIR/settings.gradle
    rm -f $OUTPUT_DIR/settings.gradle.bak
    
}

function sync_with_git {
    echo "sync_with_git"
    rsync -v --exclude=\.git --exclude=\.svn -r --delete $EXPORT_DIR/output/ $GITHUB_REPO_DIR/
    cd $GITHUB_REPO_DIR
    git add -A    
    GIT_REVISION=`git rev-list HEAD | wc -l`
    NEW_REVISION=$(($GIT_REVISION + 1))
    MSG="`cat $CHK_DIR/msg.txt` [#$SVN_REV]"
    echo "commit to Git with message: $MSG"
    git commit -m "$MSG"
    git push
}

set_vars
export_samples
sync_with_git